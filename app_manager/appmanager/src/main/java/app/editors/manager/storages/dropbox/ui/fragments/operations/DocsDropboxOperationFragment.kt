package app.editors.manager.storages.dropbox.ui.fragments.operations

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import app.documents.core.network.ApiContract
import app.editors.manager.R
import app.editors.manager.app.App
import app.editors.manager.managers.utils.Constants
import app.editors.manager.mvp.models.account.Storage
import app.editors.manager.storages.dropbox.mvp.presenters.DocsDropboxPresenter
import app.editors.manager.storages.dropbox.mvp.views.DocsDropboxView
import app.editors.manager.mvp.models.base.Entity
import app.editors.manager.mvp.models.explorer.CloudFile
import app.editors.manager.mvp.models.explorer.CloudFolder
import app.editors.manager.mvp.models.explorer.Explorer
import app.editors.manager.mvp.models.states.OperationsState
import app.editors.manager.storages.base.fragment.BaseStorageOperationsFragment
import app.editors.manager.storages.base.presenter.BaseStorageDocsPresenter
import app.editors.manager.storages.base.view.BaseStorageDocsView
import app.editors.manager.storages.dropbox.ui.fragments.DropboxSignInFragment
import app.editors.manager.ui.activities.main.OperationActivity
import app.editors.manager.ui.fragments.main.DocsBaseFragment
import app.editors.manager.ui.fragments.operations.DocsCloudOperationFragment
import moxy.presenter.InjectPresenter

class DocsDropboxOperationFragment : BaseStorageOperationsFragment() {

    companion object {

        val TAG = DocsCloudOperationFragment::class.java.simpleName

        fun newInstance(): DocsDropboxOperationFragment = DocsDropboxOperationFragment()
    }


    @InjectPresenter
    override lateinit var presenter: DocsDropboxPresenter

    override fun getOperationsPresenter() = presenter
    override fun onRefreshToken() {
        val storage = Storage(
            ApiContract.Storage.DROPBOX,
            Constants.DropBox.COM_CLIENT_ID,
            Constants.DropBox.COM_REDIRECT_URL
        )
        showFragment(DropboxSignInFragment.newInstance(storage), DropboxSignInFragment.TAG, false)
    }

    init {
        App.getApp().appComponent.inject(this)
    }


}