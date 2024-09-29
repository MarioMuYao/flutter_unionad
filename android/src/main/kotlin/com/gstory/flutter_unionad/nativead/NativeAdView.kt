package com.gstory.flutter_unionad.nativead

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.bytedance.sdk.openadsdk.*
import com.bytedance.sdk.openadsdk.TTAdDislike.DislikeInteractionCallback
import com.bytedance.sdk.openadsdk.mediation.ad.MediationAdSlot
import com.bytedance.sdk.openadsdk.mediation.ad.MediationExpressRenderListener
import com.gstory.flutter_unionad.FlutterunionadViewConfig
import com.gstory.flutter_unionad.TTAdManagerHolder
import com.gstory.flutter_unionad.UIUtils
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView


/**
 * @Description:个性化模板信息流广告
 * @Author: gstory0404@gmail
 * @CreateDate: 2020/8/20 16:31
 */
class NativeAdView(
    var context: Context,
    var activity: Activity,
    var messenger: BinaryMessenger,
    id: Int,
    params: Map<String?, Any?>
) : PlatformView {
    private val TAG = "NativeExpressAdView"
    private var mContainer: FrameLayout? = null
    private var mNativeAd: TTNativeExpressAd? = null

    //广告所需参数
    private val mCodeId: String?
    var supportDeepLink: Boolean? = true
    var viewWidth: Float
    var viewHeight: Float

    private var channel: MethodChannel?

    init {
        mCodeId = params["androidCodeId"] as String?
        supportDeepLink = params["supportDeepLink"] as Boolean?
        var width = params["width"] as Double
        var height = params["height"] as Double
        viewWidth = width.toFloat()
        viewHeight = height.toFloat()
        mContainer = FrameLayout(context)
        channel = MethodChannel(messenger, FlutterunionadViewConfig.nativeAdView + "_" + id)
        loadNativeAd()
    }

    override fun getView(): View {
        return mContainer!!
    }

    /**
     * 加载信息流广告
     */
    private fun loadNativeAd() {
        val adSlot = AdSlot.Builder()
            .setCodeId(mCodeId)
            .setSupportDeepLink(supportDeepLink!!)
            .setAdCount(1) //请求广告数量为1到3条
            .setExpressViewAcceptedSize(viewWidth, viewHeight)
            .build()

        TTAdManagerHolder.get().requestPermissionIfNecessary(activity)
        val mTTAdNative = TTAdSdk.getAdManager().createAdNative(activity)
        //加载广告
        mTTAdNative.loadNativeExpressAd(adSlot, object : TTAdNative.NativeExpressAdListener {
            override fun onError(code: Int, message: String?) {
                Log.e(TAG, "信息流广告拉去失败 $code   $message")
                mContainer!!.removeAllViews()
                channel?.invokeMethod("onFail", message)
            }

            override fun onNativeExpressAdLoad(ads: MutableList<TTNativeExpressAd>?) {
                if (ads == null || ads.isEmpty()) {
                    Log.e(TAG, "未拉取到信息流广告")
                    return
                }
                mNativeAd = ads[0]
                channel?.invokeMethod("adInfo", mNativeAd!!.getMediaExtraInfo())
                //queryEcpm()
                //展示第一条广告
                showAd()
            }
        })
    }

    /**
     * 展示广告
     */
    private fun showAd() {
        bindDislike()
        bindAdListener()
        mNativeAd?.render(); // 调用render方法进行渲染，在onRenderSuccess中展示广告
        //val manager = mNativeAd!!.mediationManager
        //if (manager != null) {
            //if (manager.isExpress) { // --- 模板feed流广告
                //bindAdListener()
                //mNativeAd?.render(); // 调用render方法进行渲染，在onRenderSuccess中展示广告
            //} else {
                //Log.e(TAG, "自渲染信息流广告 暂不支持")
                //channel?.invokeMethod("onFail", "自渲染信息流广告 暂不支持")
            //}
        //}
    }

    /**
     * 设置广告加载事件
     */
    private fun bindAdListener() {
        mNativeAd?.setExpressInteractionListener(object : TTNativeExpressAd.ExpressAdInteractionListener {            
            override fun onRenderSuccess(view: View?, width: Float, height: Float) {
                mContainer?.removeAllViews()
                mContainer?.addView(view)
                Log.e(TAG, "ExpressView width:" + view?.layoutParams?.width + " height:" + view?.layoutParams?.height)
                var map: MutableMap<String, Any?> =
                        mutableMapOf("width" to width, "height" to height)
                channel?.invokeMethod("onShow", map)
            }


            override fun onRenderFail(view: View?, msg: String?, code: Int) {
                Log.e(TAG, "ExpressView render fail:" + System.currentTimeMillis())
                channel?.invokeMethod("onFail", msg)
            }

            override fun onAdClicked(view: View?, type: Int) {
                Log.e(TAG, "广告被点击")
                channel?.invokeMethod("onClick", null)
            }

            override fun onAdShow(view: View?, type: Int) {
                Log.e(TAG, "广告展示")
            }

        })
    }

    /**
     * 设置广告的不喜欢，开发者可自定义样式
     * @param ad
     * @param customStyle 是否自定义样式，true:样式自定义
     */
    private fun bindDislike() {
        //使用默认个性化模板中默认dislike弹出样式
        mNativeAd?.setDislikeCallback(activity, object : DislikeInteractionCallback {

            override fun onSelected(p0: Int, p1: String?, p2: Boolean) {
                Log.e(TAG, "点击 $p1")
                //用户选择不喜欢原因后，移除广告展示
                mContainer!!.removeAllViews()
                channel?.invokeMethod("onDislike", p1)
            }

            override fun onCancel() {
                Log.e(TAG, "点击取消")
            }

            override fun onShow() {

            }

        })
    }

    /**
     * 获取ecpm
     */
    private fun queryEcpm() {
        var ecpmInfo = mNativeAd?.mediationManager?.showEcpm
        if (ecpmInfo != null) {
            Log.e(
                TAG, "信息流广告 ecpm: \n" +
                        "SdkName: " + ecpmInfo.sdkName + ",\n" +
                        "CustomSdkName: " + ecpmInfo.customSdkName + ",\n" +
                        "SlotId: " + ecpmInfo.slotId + ",\n" +
                        // 单位：分
                        "Ecpm: " + ecpmInfo.ecpm + ",\n" +
                        "ReqBiddingType: " + ecpmInfo.reqBiddingType + ",\n" +
                        "ErrorMsg: " + ecpmInfo.errorMsg + ",\n" +
                        "RequestId: " + ecpmInfo.requestId + ",\n" +
                        "RitType: " + ecpmInfo.ritType + ",\n" +
                        "AbTestId: " + ecpmInfo.abTestId + ",\n" +
                        "ScenarioId: " + ecpmInfo.scenarioId + ",\n" +
                        "SegmentId: " + ecpmInfo.segmentId + ",\n" +
                        "Channel: " + ecpmInfo.channel + ",\n" +
                        "SubChannel: " + ecpmInfo.subChannel + ",\n" +
                        "customData: " + ecpmInfo.customData
            )
        }
    }


    override fun dispose() {
        Log.e(TAG, "广告释放")
        mContainer?.removeAllViews()
        //调用destroy()方法释放
        mNativeAd?.destroy()
    }
}