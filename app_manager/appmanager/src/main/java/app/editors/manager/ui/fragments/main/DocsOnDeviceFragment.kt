package app.editors.manager.ui.fragments.main

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import app.documents.core.network.common.contracts.ApiContract
import app.documents.core.network.manager.models.base.Entity
import app.documents.core.network.manager.models.explorer.Item
import app.editors.manager.R
import app.editors.manager.app.App
import app.editors.manager.managers.tools.PreferenceTool
import app.editors.manager.mvp.models.states.OperationsState
import app.editors.manager.mvp.presenters.main.DocsBasePresenter
import app.editors.manager.mvp.presenters.main.DocsOnDevicePresenter
import app.editors.manager.mvp.presenters.main.OpenState
import app.editors.manager.mvp.views.main.DocsOnDeviceView
import app.editors.manager.ui.activities.login.PortalsActivity
import app.editors.manager.ui.activities.main.ActionButtonFragment
import app.editors.manager.ui.activities.main.IMainActivity
import app.editors.manager.ui.dialogs.ActionBottomDialog
import app.editors.manager.ui.dialogs.explorer.ExplorerContextItem
import app.editors.manager.ui.popup.MainPopupItem
import app.editors.manager.ui.popup.SelectPopupItem
import app.editors.manager.ui.views.custom.PlaceholderViews
import lib.toolkit.base.managers.tools.LocalContentTools
import lib.toolkit.base.managers.utils.*
import lib.toolkit.base.ui.dialogs.common.CommonDialog.Dialogs
import moxy.presenter.InjectPresenter
import java.util.*

class DocsOnDeviceFragment : DocsBaseFragment(), DocsOnDeviceView, ActionButtonFragment {

    @InjectPresenter
    override lateinit var presenter: DocsOnDevicePresenter
    private var activity: IMainActivity? = null
    private var preferenceTool: PreferenceTool? = null

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { data: Uri? ->
        data?.let { presenter.import(it) }
    }

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { data: Uri? ->
        data?.let { presenter.openFromChooser(it) }
    }

    private val readStorage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            preferenceTool?.isShowStorageAccess = false
            presenter.recreateStack()
            presenter.getItemsById(LocalContentTools.getDir(requireContext()))
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            activity = context as IMainActivity
            preferenceTool = App.getApp().appComponent.preference
        } catch (e: ClassCastException) {
            throw RuntimeException(
                DocsOnDeviceFragment::class.java.simpleName + " - must implement - " +
                        IMainActivity::class.java.simpleName
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.setSectionType(ApiContract.SectionType.DEVICE_DOCUMENTS)
        checkStorage()
        init(savedInstanceState)
    }

    override fun onStateMenuDefault(sortBy: String, isAsc: Boolean) {
        super.onStateMenuDefault(sortBy, isAsc)
        openItem?.isVisible = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.toolbar_item_main -> showActionBarMenu()
            R.id.toolbar_selection_delete -> presenter.delete()
            R.id.toolbar_item_open -> showSingleFragmentFilePicker()
        }
        return true
    }

    override fun onSwipeRefresh(): Boolean {
        if (!super.onSwipeRefresh()) {
            presenter.getItemsById(LocalContentTools.getDir(requireContext()))
            return true
        }
        return false
    }

    override fun onStateUpdateRoot(isRoot: Boolean) {
        activity?.apply {
            setAppBarStates(false)
            showNavigationButton(!isRoot)
            showAccount(false)
        }
    }

    override fun onStateMenuSelection() {
        if (menu != null && menuInflater != null && context != null) {
            menuInflater?.inflate(R.menu.docs_select, menu)
            deleteItem = menu?.findItem(R.id.toolbar_selection_delete)?.apply {
                UiUtils.setMenuItemTint(requireContext(), this, lib.toolkit.base.R.color.colorPrimary)
                isVisible = true
            }
            activity?.showNavigationButton(true)
        }
    }

    override fun onStateEmptyBackStack() {
        swipeRefreshLayout?.isRefreshing = true
        presenter.getItemsById(LocalContentTools.getDir(requireContext()))
    }

    override fun onStateUpdateFilter(isFilter: Boolean, value: String?) {
        super.onStateUpdateFilter(isFilter, value)
        activity?.showNavigationButton(isFilter)
    }

    override fun onActionBarTitle(title: String) {
        setActionBarTitle(title)
    }

    override fun onActionButtonClick(buttons: ActionBottomDialog.Buttons?) {
        when (buttons) {
            ActionBottomDialog.Buttons.IMPORT -> {
                importFile.launch(arrayOf(ActivitiesUtils.PICKER_NO_FILTER))
            }
            else -> {
                super.onActionButtonClick(buttons)
            }
        }
    }

    override fun onAcceptClick(dialogs: Dialogs?, value: String?, tag: String?) {
        var string = value
        tag?.let {
            string = string?.trim { it <= ' ' }
            when (tag) {
                TAG_STORAGE_ACCESS -> requestManage()
                DocsBasePresenter.TAG_DIALOG_BATCH_DELETE_SELECTED -> presenter.deleteItems()
                DocsBasePresenter.TAG_DIALOG_CONTEXT_RENAME -> string?.let {
                    presenter.rename(it)
                }
                DocsBasePresenter.TAG_DIALOG_ACTION_SHEET -> presenter.createDocs(
                    "$string." + ApiContract.Extension.XLSX.lowercase(Locale.ROOT)
                )
                DocsBasePresenter.TAG_DIALOG_ACTION_PRESENTATION -> presenter.createDocs(
                    "$string." + ApiContract.Extension.PPTX.lowercase(Locale.ROOT)
                )
                DocsBasePresenter.TAG_DIALOG_ACTION_DOC -> presenter.createDocs(
                    "$string." + ApiContract.Extension.DOCX.lowercase(Locale.ROOT)
                )
                DocsBasePresenter.TAG_DIALOG_ACTION_FOLDER -> string?.let {
                    presenter.createFolder(it)
                }
                DocsBasePresenter.TAG_DIALOG_DELETE_CONTEXT -> presenter.deleteFile()
                else -> {
                }
            }
        }
        hideDialog()
    }

    override fun onCancelClick(dialogs: Dialogs?, tag: String?) {
        super.onCancelClick(dialogs, tag)
        if (tag == TAG_STORAGE_ACCESS) {
            preferenceTool?.isShowStorageAccess = false
        }
    }

    override fun onContextButtonClick(contextItem: ExplorerContextItem) {
        when (contextItem) {
            ExplorerContextItem.Edit -> presenter.getFileInfo(false)
            ExplorerContextItem.Upload -> presenter.upload()
            ExplorerContextItem.Copy -> showFolderChooser(OperationsState.OperationType.COPY)
            ExplorerContextItem.Move -> showFolderChooser(OperationsState.OperationType.MOVE)
            is ExplorerContextItem.Delete -> showDeleteDialog(tag = DocsBasePresenter.TAG_DIALOG_DELETE_CONTEXT)
            else -> super.onContextButtonClick(contextItem)
        }
        contextBottomDialog?.dismiss()
    }

    override fun onActionDialog() {
        actionBottomDialog?.let {
            it.onClickListener = this
            it.isLocal = true
            it.show(parentFragmentManager, ActionBottomDialog.TAG)
        }
    }

    override fun onRemoveItems(vararg items: Item) {
        onSnackBar(resources.getQuantityString(R.plurals.operation_delete_irretrievably, items.size))
        explorerAdapter?.let { adapter ->
            adapter.removeItems(items.toList())
            adapter.checkHeaders()
            setPlaceholder(adapter.itemList.isNullOrEmpty())
            onClearMenu()
        }
    }

    override fun showDeleteDialog(count: Int, toTrash: Boolean, tag: String) {
        super.showDeleteDialog(count, false, tag)
    }

    override fun onShowDocs(uri: Uri, isNew: Boolean) {
        showEditors(uri, EditorsType.DOCS, isNew)
    }

    override fun onShowCells(uri: Uri) {
        showEditors(uri, EditorsType.CELLS)
    }

    override fun onShowSlides(uri: Uri) {
        showEditors(uri, EditorsType.PRESENTATION)
    }

    override fun onShowPdf(uri: Uri) {
        showEditors(uri, EditorsType.PDF)
    }

    override fun onOpenMedia(state: OpenState.Media) {
        showMediaActivity(state.explorer, state.isWebDav) {
            // Stub
        }
    }

    override fun onShowPortals() {
        PortalsActivity.showPortals(this)
    }

    override fun setVisibilityActionButton(isShow: Boolean) {
        activity?.showActionButton(isShow)
    }

    private fun init(savedInstanceState: Bundle?) {
        presenter.checkBackStack()
        // Check shortcut
        val bundle = requireActivity().intent?.extras
        if (savedInstanceState == null && bundle != null && bundle.containsKey(KEY_SHORTCUT)) {
            when (bundle.getString(KEY_SHORTCUT)) {
                LocalContentTools.DOCX_EXTENSION -> {
                    onActionButtonClick(ActionBottomDialog.Buttons.DOC)
                }
                LocalContentTools.XLSX_EXTENSION -> {
                    onActionButtonClick(ActionBottomDialog.Buttons.SHEET)
                }
                LocalContentTools.PPTX_EXTENSION -> {
                    onActionButtonClick(ActionBottomDialog.Buttons.PRESENTATION)
                }
            }
            requireActivity().intent.removeExtra(KEY_SHORTCUT)
        }
    }

    private fun showSingleFragmentFilePicker() {
        try {
            openFile.launch(arrayOf(ActivitiesUtils.PICKER_NO_FILTER))
        } catch (e: ActivityNotFoundException) {
            onError(e.message)
        }
    }

    override fun onError(message: String?) {
        if (message?.contains(getString(R.string.errors_import_local_file_desc)) == true) {
            showSnackBar(R.string.errors_import_local_file)
        } else {
            super.onError(message)
        }
    }

    private fun checkStorage() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> {
                requestReadWritePermission()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                requestAccessStorage()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestAccessStorage() {
        if (!Environment.isExternalStorageManager() && preferenceTool?.isShowStorageAccess == true) {
            showQuestionDialog(
                getString(R.string.app_manage_files_title),
                getString(R.string.app_manage_files_description),
                getString(R.string.dialogs_common_ok_button),
                getString(R.string.dialogs_common_cancel_button),
                TAG_STORAGE_ACCESS
            )
        }
    }

    private fun requestReadWritePermission() {
        RequestPermissions(requireActivity().activityResultRegistry, { permissions ->
            if (permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true && permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true) {
                presenter.recreateStack()
                presenter.getItemsById(LocalContentTools.getDir(requireContext()))
            } else {
                swipeRefreshLayout?.isEnabled = false
                openItem?.isVisible = true
                activity?.showActionButton(false)
                placeholderViews?.setTemplatePlaceholder(PlaceholderViews.Type.ACCESS)
            }
        }, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)).request()
    }

    private fun requestManage() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                readStorage.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + requireContext().packageName)
                    )
                )
            }
        } catch (e: ActivityNotFoundException) {
            openItem?.isVisible = false
            swipeRefreshLayout?.isEnabled = false
            activity?.showActionButton(false)
            placeholderViews?.setTemplatePlaceholder(PlaceholderViews.Type.ACCESS)
        }
    }

    private fun setPlaceholder(isEmpty: Boolean) {
        onPlaceholder(if (isEmpty) PlaceholderViews.Type.EMPTY else PlaceholderViews.Type.NONE)
    }

    override fun showMainActionPopup(vararg excluded: MainPopupItem) {
        super.showMainActionPopup(MainPopupItem.SortBy.Author)
    }

    override fun showSelectActionPopup(vararg excluded: SelectPopupItem) {
        super.showSelectActionPopup(SelectPopupItem.Operation.Restore, SelectPopupItem.Download)
    }

    fun showRoot() {
        presenter.recreateStack()
        presenter.getItemsById(LocalContentTools.getDir(requireContext()))
        presenter.updateState()
        onScrollToPosition(0)
    }

    private fun showFolderChooser(operation: OperationsState.OperationType) {
        FolderChooser(requireActivity().activityResultRegistry, { data ->
            data?.let { uri ->
                presenter.moveFile(uri, operation == OperationsState.OperationType.COPY)
            }
        }).show()
    }

    override val selectActionBarClickListener: (SelectPopupItem) -> Unit = { item ->
        when (item) {
            is SelectPopupItem.Operation -> showFolderChooser(item.value)
            else -> super.selectActionBarClickListener(item)
        }
    }

    override val isActivePage: Boolean
        get() = isAdded

    override val isWebDav: Boolean
        get() = false

    companion object {
        val TAG: String = DocsOnDeviceFragment::class.java.simpleName

        private const val TAG_STORAGE_ACCESS = "TAG_STORAGE_ACCESS"

        private const val KEY_SHORTCUT = "create_type"

        fun newInstance(): DocsOnDeviceFragment {
            return DocsOnDeviceFragment()
        }
    }
}