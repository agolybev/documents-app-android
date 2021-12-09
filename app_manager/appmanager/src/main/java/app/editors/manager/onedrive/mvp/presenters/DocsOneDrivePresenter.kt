package app.editors.manager.onedrive.mvp.presenters

import android.accounts.Account
import android.content.ClipData
import android.net.Uri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.documents.core.account.Recent
import app.documents.core.network.ApiContract
import app.editors.manager.R
import app.editors.manager.app.App
import app.editors.manager.app.oneDriveAuthService
import app.editors.manager.managers.receivers.DownloadReceiver
import app.editors.manager.managers.receivers.UploadReceiver
import app.editors.manager.managers.utils.Constants
import app.editors.manager.managers.utils.StorageUtils
import app.editors.manager.mvp.models.explorer.CloudFile
import app.editors.manager.mvp.models.explorer.CloudFolder
import app.editors.manager.mvp.models.explorer.Explorer
import app.editors.manager.mvp.models.explorer.Item
import app.editors.manager.mvp.models.models.ModelExplorerStack
import app.editors.manager.mvp.models.request.RequestCreate
import app.editors.manager.mvp.presenters.main.DocsBasePresenter
import app.editors.manager.onedrive.managers.providers.OneDriveFileProvider
import app.editors.manager.onedrive.managers.utils.OneDriveUtils
import app.editors.manager.onedrive.managers.works.DownloadWork
import app.editors.manager.onedrive.managers.works.UploadWork
import app.editors.manager.onedrive.mvp.models.request.ExternalLinkRequest
import app.editors.manager.onedrive.mvp.models.response.AuthResponse
import app.editors.manager.onedrive.mvp.views.DocsOneDriveView
import app.editors.manager.onedrive.onedrive.OneDriveResponse
import app.editors.manager.ui.dialogs.ContextBottomDialog
import app.editors.manager.ui.views.custom.PlaceholderViews
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lib.toolkit.base.managers.utils.AccountUtils
import lib.toolkit.base.managers.utils.KeyboardUtils
import lib.toolkit.base.managers.utils.StringUtils
import lib.toolkit.base.managers.utils.TimeUtils
import moxy.InjectViewState
import retrofit2.HttpException
import java.util.*


@InjectViewState
class DocsOneDrivePresenter: DocsBasePresenter<DocsOneDriveView>(),
    UploadReceiver.OnUploadListener, DownloadReceiver.OnDownloadListener {

    private var downloadDisposable: Disposable? = null
    private var tempFile: CloudFile? = null
    private val workManager = WorkManager.getInstance()

    private val uploadReceiver: UploadReceiver
    private val downloadReceiver: DownloadReceiver


    val externalLink : Unit
        get() {
            mItemClicked?.let {
                val request = ExternalLinkRequest(
                    type = OneDriveUtils.VAL_SHARE_TYPE_READ_WRITE,
                    scope = OneDriveUtils.VAL_SHARE_SCOPE_ANON
                )
                (mFileProvider as OneDriveFileProvider).share(it.id, request)?.let { extrenalLinkResponse ->
                    mDisposable.add(extrenalLinkResponse
                        .subscribe( {response ->
                            it.shared = !it.shared
                            response.link?.webUrl?.let { link ->
                                KeyboardUtils.setDataToClipboard(
                                    mContext,
                                    link,
                                    mContext.getString(R.string.share_clipboard_external_link_label)
                                )
                            }
                            viewState.onDocsAccess(
                                true,
                                mContext.getString(R.string.share_clipboard_external_copied)
                            )
                        }) {throwable: Throwable -> fetchError(throwable)}
                    )
                }
            }
        }

    init {
        App.getApp().appComponent.inject(this)
        mModelExplorerStack = ModelExplorerStack()
        mFilteringValue = ""
        mPlaceholderType = PlaceholderViews.Type.NONE
        mIsContextClick = false
        mIsFilteringMode = false
        mIsSelectionMode = false
        mIsFoldersMode = false
        uploadReceiver = UploadReceiver()
        downloadReceiver = DownloadReceiver()
    }

    override fun onFirstViewAttach() {
        super.onFirstViewAttach()
        uploadReceiver.setOnUploadListener(this)
        LocalBroadcastManager.getInstance(mContext).registerReceiver(uploadReceiver, uploadReceiver.filter)
        downloadReceiver.setOnDownloadListener(this)
        LocalBroadcastManager.getInstance(mContext).registerReceiver(downloadReceiver, downloadReceiver.filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(uploadReceiver)
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(downloadReceiver)
    }

    fun getProvider() {
        mFileProvider?.let {
            CoroutineScope(Dispatchers.Default).launch {
                App.getApp().appComponent.accountsDao.getAccountOnline()?.let {
                    withContext(Dispatchers.Main) {
                        getItemsById(null)
                    }

                }
            }
        } ?: run {
            CoroutineScope(Dispatchers.Default).launch {
                App.getApp().appComponent.accountsDao.getAccountOnline()?.let { cloudAccount ->
                    AccountUtils.getAccount(mContext, cloudAccount.getAccountName())?.let {
                        mFileProvider = OneDriveFileProvider()
                        withContext(Dispatchers.Main) {
                            getItemsById(null)
                        }
                    }
                } ?: run {
                    throw Error("Not accounts")
                }
            }
        }
    }

    fun refreshToken() {
        val account = Account(App.getApp().appComponent.accountOnline?.getAccountName(), mContext.getString(lib.toolkit.base.R.string.account_type))
        val accData = AccountUtils.getAccountData(mContext, account)
        val map = mapOf(
            StorageUtils.ARG_CLIENT_ID to Constants.OneDrive.COM_CLIENT_ID,
            StorageUtils.ARG_SCOPE to StorageUtils.OneDrive.VALUE_SCOPE,
            StorageUtils.ARG_REDIRECT_URI to Constants.OneDrive.COM_REDIRECT_URL,
            StorageUtils.OneDrive.ARG_GRANT_TYPE to StorageUtils.OneDrive.VALUE_GRANT_TYPE_REFRESH,
            StorageUtils.OneDrive.ARG_CLIENT_SECRET to Constants.OneDrive.COM_CLIENT_SECRET,
            StorageUtils.OneDrive.ARG_REFRESH_TOKEN to accData.refreshToken!!
        )
        mDisposable.add(App.getApp().oneDriveAuthService.getToken(map)
            .subscribe {oneDriveResponse ->
                when(oneDriveResponse) {
                    is OneDriveResponse.Success -> {
                        AccountUtils.setAccountData(mContext, account, accData.copy(accessToken = (oneDriveResponse.response as AuthResponse).access_token))
                        AccountUtils.setToken(mContext, account, oneDriveResponse.response.access_token)
                        (mFileProvider as OneDriveFileProvider).refreshInstance()
                        refresh()
                    }
                    is OneDriveResponse.Error -> {
                        throw oneDriveResponse.error
                    }
                }
            })
    }

    override fun download(downloadTo: Uri) {
        val data = Data.Builder()
            .putString(DownloadWork.FILE_ID_KEY, mItemClicked?.id)
            .putString(DownloadWork.FILE_URI_KEY, downloadTo.toString())
            .build()

        val request = OneTimeWorkRequest.Builder(DownloadWork::class.java)
            .setInputData(data)
            .build()

        workManager.enqueue(request)
    }

    override fun createDownloadFile() {
        if (mModelExplorerStack.selectedFiles.isNotEmpty() || mModelExplorerStack.selectedFolders.isNotEmpty() || mItemClicked is CloudFolder) {
            viewState.onCreateDownloadFile(DownloadWork.DOWNLOAD_ZIP_NAME)
        } else if (mItemClicked is CloudFile) {
            viewState.onCreateDownloadFile((mItemClicked as CloudFile).title)
        }
    }

    override fun getNextList() {
        val id = mModelExplorerStack.currentId
        val loadPosition = mModelExplorerStack.loadPosition
        if (id != null && loadPosition > 0) {
            val args = getArgs(mFilteringValue)
            args[ApiContract.Parameters.ARG_START_INDEX] = loadPosition.toString()
            mDisposable.add(mFileProvider.getFiles(id, args)!!.subscribe({ explorer: Explorer? ->
                mModelExplorerStack.addOnNext(explorer)
                val last = mModelExplorerStack.last()
                if (last != null) {
                    viewState.onDocsNext(getListWithHeaders(last, true))
                }
            }) { throwable: Throwable? -> fetchError(throwable) })
        }
    }

    override fun createDocs(title: String) {
        val id = mModelExplorerStack.currentId
        id?.let {
            val requestCreate = RequestCreate()
            requestCreate.title = title
            mDisposable.add(mFileProvider.createFile(id, requestCreate).subscribe({ file: CloudFile? ->
                addFile(file)
                setPlaceholderType(PlaceholderViews.Type.NONE)
                viewState.onDialogClose()
                viewState.onOpenLocalFile(file)
            }) { throwable: Throwable? -> fetchError(throwable) })
            showDialogWaiting(TAG_DIALOG_CANCEL_SINGLE_OPERATIONS)
        }
    }

    override fun getFileInfo() {
        if (mItemClicked != null && mItemClicked is CloudFile) {
            val file = mItemClicked as CloudFile
            val extension = file.fileExst
            if (StringUtils.isImage(extension)) {
                addRecent(file)
                return
            }
        }
        showDialogWaiting(TAG_DIALOG_CANCEL_UPLOAD)
        downloadDisposable = mFileProvider.fileInfo(mItemClicked!!)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { file: CloudFile? ->
                    tempFile = file
                    viewState.onDialogClose()
                    viewState.onOpenLocalFile(file)
                }
            ) { throwable: Throwable? -> fetchError(throwable) }

    }

    fun upload(uri: Uri?, uris: ClipData?, tag: String) {
        val uploadUris = mutableListOf<Uri>()
        var index = 0

        if(uri != null) {
            uploadUris.add(uri)
        } else if(uris != null) {
            while(index != uris.itemCount) {
                uploadUris.add(uris.getItemAt(index).uri)
                index++
            }
        }

        for (uri in uploadUris) {
            val data = Data.Builder()
                .putString(UploadWork.KEY_FOLDER_ID, mModelExplorerStack.currentId)
                .putString(UploadWork.KEY_FROM, uri.toString())
                .putString(UploadWork.KEY_TAG, tag)
                .build()

            val request = OneTimeWorkRequest.Builder(UploadWork::class.java)
                .setInputData(data)
                .build()

            workManager.enqueue(request)
        }

    }

    override fun addRecent(file: CloudFile?) {
        CoroutineScope(Dispatchers.Default).launch {
            accountDao.getAccountOnline()?.let {
                file?.title?.let { fileName ->
                    Recent(
                        idFile = if (file.fileExst?.let { fileExt -> StringUtils.isImage(fileExt) } == true) file.id else file.viewUrl,
                        path = file.webUrl,
                        name = fileName,
                        size = file.pureContentLength,
                        isLocal = false,
                        isWebDav = true,
                        date = Date().time,
                        ownerId = it.id,
                        source = it.portal
                    )
                }?.let { recent ->
                    recentDao.addRecent(
                        recent
                    )
                }
            }
        }
    }

    override fun onContextClick(item: Item?, position: Int, isTrash: Boolean) {
        onClickEvent(item, position)
        mIsContextClick = true
        val state = ContextBottomDialog.State()
        state.title = itemClickedTitle
        state.info = TimeUtils.formatDate(itemClickedDate)
        state.isFolder = !isClickedItemFile
        state.isDocs = isClickedItemDocs
        state.isWebDav = false
        state.isOneDrive = true
        state.isTrash = isTrash
        state.isItemEditable = true
        state.isContextEditable = true
        state.isCanShare = true
        if (!isClickedItemFile) {
            state.iconResId = R.drawable.ic_type_folder
        } else {
            state.iconResId = getIconContext(
                StringUtils.getExtensionFromPath(
                    itemClickedTitle
                )
            )
        }
        state.isPdf = isPdf
        if (state.isShared && state.isFolder) {
            state.iconResId = R.drawable.ic_type_folder_shared
        }
        viewState.onItemContext(state)
    }

    override fun onActionClick() {
        viewState.onActionDialog(false, true)
    }

    override fun updateViewsState() {
        if (mIsSelectionMode) {
            viewState.onStateUpdateSelection(true)
            viewState.onActionBarTitle(mModelExplorerStack.countSelectedItems.toString())
            viewState.onStateAdapterRoot(mModelExplorerStack.isNavigationRoot)
            viewState.onStateActionButton(false)
        } else if (mIsFilteringMode) {
            viewState.onActionBarTitle(mContext.getString(R.string.toolbar_menu_search_result))
            viewState.onStateUpdateFilter(true, mFilteringValue)
            viewState.onStateAdapterRoot(mModelExplorerStack.isNavigationRoot)
            viewState.onStateActionButton(false)
        } else if (!mModelExplorerStack.isRoot) {
            viewState.onStateAdapterRoot(false)
            viewState.onStateUpdateRoot(false)
            viewState.onStateActionButton(true)
            viewState.onActionBarTitle(if(currentTitle.isEmpty()) { mItemClicked?.title } else { currentTitle } )
        } else {
            if (mIsFoldersMode) {
                viewState.onActionBarTitle(mContext.getString(R.string.operation_title))
                viewState.onStateActionButton(false)
            } else {
                viewState.onActionBarTitle("")
                viewState.onStateActionButton(true)
            }
            viewState.onStateAdapterRoot(true)
            viewState.onStateUpdateRoot(true)
        }
    }
    override fun move(): Boolean {
        return if (super.move()) {
            transfer(ApiContract.Operation.DUPLICATE, true)
            true
        } else {
            false
        }
    }
    override fun copy(): Boolean {
        return if (super.move()) {
            transfer(ApiContract.Operation.DUPLICATE, false)
            true
        } else {
            false
        }
    }

    override fun delete(): Boolean {
        if (mModelExplorerStack.countSelectedItems > 0) {
            viewState.onDialogQuestion(
                mContext.getString(R.string.dialogs_question_delete), null,
                TAG_DIALOG_BATCH_DELETE_SELECTED
            )
        } else {
            deleteItems()
        }
        return true
    }

    override fun fetchError(throwable: Throwable?) {
        super.fetchError(throwable)
        if(throwable is HttpException) {
            if(throwable.code() == 423) {
                viewState.onError(App.getApp().applicationContext.getString(R.string.storage_onedrive_error_opened))
            }
            if(throwable.code() == 409) {
                viewState.onError(App.getApp().applicationContext.getString(R.string.storage_onedrive_error_exist))
            }
        }
    }

    override fun onUploadError(path: String?, info: String?, file: String?) {
        info?.let { viewState.onSnackBar(it) }
    }

    override fun onUploadComplete(
        path: String?,
        info: String?,
        title: String?,
        file: CloudFile?,
        id: String?
    ) {
        info?.let { viewState.onSnackBar(it) }
        refresh()
        viewState.onDeleteUploadFile(id)
    }

    override fun onUploadAndOpen(path: String?, title: String?, file: CloudFile?, id: String?) {
        //viewState.onFileWebView(file)
    }

    override fun onUploadFileProgress(progress: Int, id: String?, folderId: String?) {
        if (mModelExplorerStack.currentId == folderId) {
            viewState.onUploadFileProgress(progress, id)
        }
    }

    override fun onUploadCanceled(path: String?, info: String?, id: String?) {
        info?.let { viewState.onSnackBar(it) }
        viewState.onDeleteUploadFile(id)
        if (app.editors.manager.managers.works.UploadWork.getUploadFiles(mModelExplorerStack.currentId)?.isEmpty() == true) {
            viewState.onRemoveUploadHead()
            getListWithHeaders(mModelExplorerStack.last(), true)
        }
    }

    override fun onUploadRepeat(path: String?, info: String?) {
        viewState.onDialogClose()
        info?.let { viewState.onSnackBar(it) }
    }

    override fun onDownloadError(id: String?, url: String?, title: String?, info: String?) {
        info?.let { viewState.onSnackBar(it) }
    }

    override fun onDownloadProgress(id: String?, total: Int, progress: Int) {
        viewState.onDialogProgress(total, progress)
    }

    override fun onDownloadComplete(
        id: String?,
        url: String?,
        title: String?,
        info: String?,
        path: String?,
        mime: String?,
        uri: Uri?
    ) {
        viewState.onDialogClose()
        viewState.onSnackBarWithAction(
            """
    $info
    $title
    """.trimIndent(), mContext.getString(R.string.download_manager_open)
        ) { showDownloadFolderActivity(uri) }
    }

    override fun onDownloadCanceled(id: String?, info: String?) {
        viewState.onDialogClose()
        info?.let { viewState.onSnackBar(it) }
    }

    override fun onDownloadRepeat(id: String?, title: String?, info: String?) {
        viewState.onDialogClose()
        info?.let { viewState.onSnackBar(it) }
    }

    private fun showDownloadFolderActivity(uri: Uri?) {
        viewState.onDownloadActivity(uri)
    }
}