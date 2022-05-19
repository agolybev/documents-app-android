package app.editors.manager.mvp.presenters.main

import android.net.Uri
import app.documents.core.account.CloudAccount
import app.documents.core.account.copyWithToken
import app.documents.core.network.ApiContract
import app.editors.manager.BuildConfig
import app.editors.manager.R
import app.editors.manager.app.App
import app.editors.manager.mvp.models.models.OpenDataModel
import app.editors.manager.mvp.presenters.base.BasePresenter
import app.editors.manager.mvp.views.main.MainActivityView
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.tasks.Task
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lib.toolkit.base.managers.utils.AccountUtils
import lib.toolkit.base.managers.utils.CryptUtils
import moxy.InjectViewState

sealed class MainActivityState {
    object RecentState : MainActivityState()
    object OnDeviceState : MainActivityState()
    object SettingsState : MainActivityState()
    class CloudState(val account: CloudAccount? = null) : MainActivityState()
}

@InjectViewState
class MainActivityPresenter : BasePresenter<MainActivityView>() {

    companion object {
        val TAG: String = MainActivityPresenter::class.java.simpleName
        const val TAG_DIALOG_REMOTE_PLAY_MARKET = "TAG_DIALOG_REMOTE_PLAY_MARKET"
        const val TAG_DIALOG_REMOTE_APP = "TAG_DIALOG_REMOTE_APP"
        const val TAG_DIALOG_RATE_FIRST = "TAG_DIALOG_RATE_FIRST"
        private const val TAG_DIALOG_RATE_SECOND = "TAG_DIALOG_RATE_SECOND"
        private const val TAG_DIALOG_RATE_FEEDBACK = "TAG_DIALOG_RATE_FEEDBACK"
        private const val DEFAULT_RATE_SESSIONS: Long = 5
    }

    init {
        App.getApp().appComponent.inject(this)
    }

    private val disposable = CompositeDisposable()

    private var cloudAccount: CloudAccount? = null
    private var reviewInfo: ReviewInfo? = null
    private var isAppColdStart = true

    var isDialogOpen: Boolean = false

    override fun onFirstViewAttach() {
        super.onFirstViewAttach()
        preferenceTool.setUserSession()
        if (isAppColdStart) {
            isAppColdStart = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.dispose()
    }

    fun init(isPortal: Boolean = false) {
        CoroutineScope(Dispatchers.Default).launch {
            accountDao.getAccountOnline()?.let {
                cloudAccount = it
                setNetworkSetting(it)
                withContext(Dispatchers.Main) {
                    checkToken(it)
                }
            } ?: run {
                withContext(Dispatchers.Main) {
                    if (isPortal) {
                        viewState.onRender(MainActivityState.CloudState())
                    } else {
                        viewState.onRender(MainActivityState.OnDeviceState)
                    }
                }
            }
        }
    }

    private fun checkToken(cloudAccount: CloudAccount) {
        if (cloudAccount.isWebDav) {
            viewState.onRender(MainActivityState.CloudState(cloudAccount))
        } else {
            AccountUtils.getToken(
                context = context,
                accountName = cloudAccount.getAccountName()
            )?.let {
                viewState.onRender(MainActivityState.CloudState(cloudAccount))
            } ?: run {
                viewState.onRender(MainActivityState.CloudState())
            }
        }
    }

    private fun setNetworkSetting(cloudAccount: CloudAccount) {
        networkSettings.setBaseUrl(cloudAccount.portal ?: ApiContract.DEFAULT_HOST)
        networkSettings.setScheme(cloudAccount.scheme ?: ApiContract.SCHEME_HTTPS)
        networkSettings.setSslState(cloudAccount.isSslState)
        networkSettings.setCipher(cloudAccount.isSslCiphers)
        networkSettings.serverVersion = cloudAccount.serverVersion
    }

    fun getRemoteConfigRate() {
        if (!BuildConfig.DEBUG) {
            if (preferenceTool.isRateOn && preferenceTool.userSession % DEFAULT_RATE_SESSIONS == 0L) {
                viewState.onRatingApp()
            }
        }
    }

    private fun getReviewInfo() {
        ReviewManagerFactory.create(context)
            .requestReviewFlow()
            .addOnCompleteListener { task: Task<ReviewInfo?> ->
                if (task.isSuccessful) {
                    reviewInfo = task.result
                }
            }
    }

    fun setAccount() {
        CoroutineScope(Dispatchers.Default).launch {
            accountDao.getAccountOnline()?.let {
                cloudAccount = it
            }
        }
    }

    fun onAcceptClick(value: String?, tag: String?) {
        tag?.let {
            when (tag) {
                TAG_DIALOG_REMOTE_PLAY_MARKET -> {
                    viewState.onShowPlayMarket(BuildConfig.RELEASE_ID)
                    viewState.onDialogClose()
                }
                TAG_DIALOG_REMOTE_APP -> {
                    viewState.onShowApp(BuildConfig.RELEASE_ID)
                    viewState.onDialogClose()
                }
                TAG_DIALOG_RATE_FIRST -> {
                    getReviewInfo()
                    viewState.onQuestionDialog(
                        context.getString(R.string.dialogs_question_rate_second_info), TAG_DIALOG_RATE_SECOND,
                        context.getString(R.string.dialogs_question_accept_sure),
                        context.getString(R.string.dialogs_question_accept_no_thanks), null
                    )
                }
                TAG_DIALOG_RATE_SECOND -> {
                    viewState.onDialogClose()
                    reviewInfo?.let {
                        viewState.onShowInAppReview(it)
                    } ?: run {
                        viewState.onShowPlayMarket(BuildConfig.RELEASE_ID)
                    }
                }
                TAG_DIALOG_RATE_FEEDBACK -> {
                    if (value != null) {
                        viewState.onShowEmailClientTemplate(value)
                    }
                    viewState.onDialogClose()
                }
            }
        }
    }

    fun onCancelClick(tag: String?) {
        tag?.let {
            when (tag) {
                TAG_DIALOG_RATE_FIRST -> {
                    preferenceTool.isRateOn = false
                    viewState.onDialogClose()
                    viewState.onShowEditMultilineDialog(
                        context.getString(R.string.dialogs_edit_feedback_rate_title),
                        context.getString(R.string.dialogs_edit_feedback_rate_hint),
                        context.getString(R.string.dialogs_edit_feedback_rate_accept),
                        context.getString(R.string.dialogs_question_accept_no_thanks), TAG_DIALOG_RATE_FEEDBACK
                    )
                }
                TAG_DIALOG_RATE_SECOND -> {
                    preferenceTool.isRateOn = false
                    viewState.onDialogClose()
                }
            }
        }
    }

    fun checkPassCode(isCode: Boolean? = null) {
        if (preferenceTool.isPasscodeLockEnable && isCode == null) {
            viewState.onCodeActivity()
        }
    }

    fun navigationItemClick(itemId: Int) {
        when (itemId) {
            R.id.menu_item_recent -> viewState.onRender(MainActivityState.RecentState)
            R.id.menu_item_on_device -> viewState.onRender(MainActivityState.OnDeviceState)
            R.id.menu_item_settings -> viewState.onRender(MainActivityState.SettingsState)
            R.id.menu_item_cloud -> {
                CoroutineScope(Dispatchers.Default).launch {
                    cloudAccount = accountDao.getAccountOnline()
                    withContext(Dispatchers.Main) {
                        viewState.onRender(MainActivityState.CloudState(cloudAccount))
                    }
                }
            }
        }
    }

    fun clear() {
        CoroutineScope(Dispatchers.Default).launch {
            accountDao.getAccountOnline()?.let {
                accountDao.updateAccount(
                    it.copyWithToken(
                        isOnline = false
                    )
                )
            }
            networkSettings.setDefault()
            withContext(Dispatchers.Main) {
                viewState.onRender(MainActivityState.CloudState())
            }
        }
    }

    fun checkFileData(fileData: Uri) {
        CoroutineScope(Dispatchers.Default).launch {
            accountDao.getAccountOnline()?.let { account ->
                val data = Json.decodeFromString<OpenDataModel>(CryptUtils.decodeUri(fileData.query))
                if (data.portal?.equals(
                        account.portal,
                        ignoreCase = true
                    ) == true &&
                    data.email?.equals(account.login, ignoreCase = true) == true
                ) {
                    withContext(Dispatchers.Main) {
                        viewState.openFile(account, Json.encodeToString(data))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        viewState.onOpenProjectFileError(context.getString(R.string.error_open_project_file))
                    }
                }

            }
        }
    }

    fun onRateOff() {
        preferenceTool.isRateOn = false
    }

}