/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.rocket.tabs

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import mozilla.components.browser.session.Session.SecurityInfo
import mozilla.components.support.base.observer.Observable
import mozilla.components.support.base.observer.ObserverRegistry
import org.mozilla.rocket.tabs.Session.Observer
import org.mozilla.rocket.tabs.TabView.FindListener
import org.mozilla.rocket.tabs.web.DownloadCallback
import java.util.UUID
import kotlin.properties.Delegates

const val TAG = "Session"

class Session @JvmOverloads constructor(
        val id: String = UUID.randomUUID().toString(),
        var parentId: String? = "",
        var initialUrl: String? = "",
        private val delegate: Observable<Observer> = ObserverRegistry()
) : Observable<Observer> by delegate {

    var tabView: TabView? = null
        private set

    private var downloadCallback: DownloadCallback? = null
    private var findListener: TabView.FindListener? = null

    var engineSession: TabViewEngineSession? = null
    var engineObserver: TabViewEngineSession.Observer? = null

    var webViewState: Bundle? = null

    var favicon: Bitmap? = null

    val securityState: Int
        @SiteIdentity.SecurityState
        get() = if (tabView == null) {
            SiteIdentity.UNKNOWN
        } else tabView!!.securityState

    val isFromExternal: Boolean
        get() = ID_EXTERNAL == parentId

    /**
     * The currently loading or loaded URL.
     */
    var url: String? by Delegates.observable(initialUrl) { _, old, new ->
        if (old != null && new != null) {
            notifyObservers(old, new) { onUrlChanged(this@Session, new) }
        }
    }

    /**
     * The title of the currently displayed website changed.
     */
    var title: String by Delegates.observable("") { _, old, new ->
        notifyObservers(old, new) { onTitleChanged(this@Session, new) }
    }

    /**
     * The progress loading the current URL.
     */
    var progress: Int by Delegates.observable(0) { _, old, new ->
        notifyObservers(old, new) { onProgress(this@Session, new) }
    }

    /**
     * Loading state, true if this session's url is currently loading, otherwise false.
     */
    var loading: Boolean by Delegates.observable(false) { _, old, new ->
        notifyObservers(old, new) { onLoadingStateChanged(this@Session, new) }
    }

    /**
     * Security information indicating whether or not the current session is
     * for a secure URL, as well as the host and SSL certificate authority, if applicable.
     */
    var securityInfo: SecurityInfo by Delegates.observable(SecurityInfo()) { _, old, new ->
        notifyObservers(old, new) { onSecurityChanged(this@Session, new.secure) }
    }

    /**
     * To sync session's properties to view, before saving. This method would be retired once we
     * involve Observable class for those properties.
     */
    fun syncFromView() {
        if (webViewState == null) {
            webViewState = Bundle()
        }
        if (tabView != null) {
            this.title = tabView!!.title
            if (TextUtils.equals(tabView!!.url, this.url)) {
                this.url = tabView!!.url
            }
            tabView!!.saveViewState(this.webViewState)
        }
    }

    fun isValid(): Boolean {
        return id.isNotBlank() && (url?.isNotBlank() ?: false)
    }

    internal fun setDownloadCallback(callback: DownloadCallback?) {
        downloadCallback = callback
    }

    internal fun setFindListener(listener: FindListener?) {
        findListener = listener
        if (tabView != null) {
            tabView!!.setFindListener(listener)
        }
    }

    fun setContentBlockingEnabled(enabled: Boolean) {
        if (tabView != null) {
            tabView!!.setContentBlockingEnabled(enabled)
        }
    }

    fun setImageBlockingEnabled(enabled: Boolean) {
        if (tabView != null) {
            tabView!!.setImageBlockingEnabled(enabled)
        }
    }

    fun hasParentTab(): Boolean {
        return !isFromExternal && !TextUtils.isEmpty(parentId)
    }

    /**
     * To detach @see{android.view.View} of this tab, if any, is detached from its parent.
     */
    fun detach() {
        val hasParentView = (tabView != null
                && tabView!!.view != null
                && tabView!!.view.parent != null)
        if (hasParentView) {
            val parent = tabView!!.view.parent as ViewGroup
            parent.removeView(tabView!!.view)
        }
    }

    /* package */ internal fun destroy() {
        setDownloadCallback(null)
        setFindListener(null)
        engineSession?.unregisterObservers()
        unregisterObservers()

        if (tabView != null) {
            // ensure the view not bind to parent
            detach()

            tabView!!.destroy()
        }
    }

    /* package */ internal fun resume() {
        if (tabView != null) {
            tabView!!.onResume()
        }
    }

    /* package */ internal fun pause() {
        if (tabView != null) {
            tabView!!.onPause()
        }
    }

    /* package */ internal fun initializeView(provider: TabViewProvider): TabView? {
        val url = if (TextUtils.isEmpty(this.url)) this.initialUrl else this.url
        if (tabView == null) {
            tabView = provider.create()

            engineSession = TabViewEngineSession()
            engineSession?.tabView = tabView
            engineObserver = TabViewEngineObserver(this, tabView!!)
            engineSession?.register(engineObserver!!)

            tabView!!.setDownloadCallback(downloadCallback)
            tabView!!.setFindListener(findListener)

            if (webViewState != null) {
                tabView!!.restoreViewState(webViewState)
            } else if (!TextUtils.isEmpty(url)) {
                tabView!!.loadUrl(url)
            }
        }

        return tabView
    }

    /**
     * Helper method to notify observers.
     */
    private fun notifyObservers(old: Any, new: Any, block: Observer.() -> Unit) {
        if (old != new) {
            notifyObservers(block)
        }
    }

    interface Observer {

        fun onLoadingStateChanged(session: Session, loading: Boolean) = Unit

        fun onSecurityChanged(session: Session, isSecure: Boolean) = Unit

        fun onUrlChanged(session: Session, url: String?) = Unit

        /**
         * Return true if the URL was handled, false if we should continue loading the current URL.
         */
        fun handleExternalUrl(url: String?): Boolean = false

        fun updateFailingUrl(url: String?, updateFromError: Boolean) = Unit

        fun onCreateWindow(isDialog: Boolean, isUserGesture: Boolean, msg: Message?) = false

        fun onCloseWindow(tabView: TabView?) = Unit

        fun onProgress(session: Session, progress: Int) = Unit


        /**
         * @see android.webkit.WebChromeClient
         */
        fun onShowFileChooser(tabView: TabView,
                              filePathCallback: ValueCallback<Array<Uri>>,
                              fileChooserParams: WebChromeClient.FileChooserParams) = false

        fun onTitleChanged(session: Session, title: String?) = Unit

        fun onReceivedIcon(icon: Bitmap?) = Unit

        fun onLongPress(session: Session, hitTarget: TabView.HitTarget) = Unit

        /**
         * Notify the host application that the current page has entered full screen mode.
         * <p>
         * The callback needs to be invoked to request the page to exit full screen mode.
         * <p>
         * Some TabView implementations may pass a custom View which contains the web contents in
         * full screen mode.
         */
        fun onEnterFullScreen(callback: TabView.FullscreenCallback, view: View?) = Unit

        /**
         * Notify the host application that the current page has exited full screen mode.
         * <p>
         * If a View was passed when the application entered full screen mode then this view must
         * be hidden now.
         */
        fun onExitFullScreen() = Unit

        fun onGeolocationPermissionsShowPrompt(origin: String,
                                               callback: GeolocationPermissions.Callback?) = Unit
    }

    companion object {
        const val ID_EXTERNAL = "_open_from_external_"
    }
}
