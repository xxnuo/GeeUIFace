package com.geeui.face.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import com.geeui.face.consts.RGestureConsts
import com.geeui.face.gesture.GestureCallback
import com.geeui.face.gesture.GestureDataThreadExecutor
import com.geeui.face.model.IdentFaceModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.letianpai.robot.components.network.nets.GeeUiNetManager
import com.letianpai.robot.letianpaiservice.LtpAppCmdCallback
import com.letianpai.robot.letianpaiservice.LtpExpressionCallback
import com.letianpai.robot.letianpaiservice.LtpLongConnectCallback
import com.renhejia.robot.commandlib.consts.AppCmdConsts
import com.renhejia.robot.commandlib.consts.MCUCommandConsts
import com.renhejia.robot.commandlib.consts.RobotRemoteConsts
import com.renhejia.robot.commandlib.consts.SpeechConst
import com.renhejia.robot.commandlib.log.LogUtils
import com.renhejia.robot.commandlib.parser.antennalight.AntennaLight
import com.renhejia.robot.commandlib.parser.antennamotion.AntennaMotion
import com.renhejia.robot.commandlib.parser.face.Face
import com.renhejia.robot.commandlib.parser.motion.Motion
import com.renhejia.robot.commandlib.parser.power.PowerMotion
import com.renhejia.robot.commandlib.parser.sound.Sound
import com.renhejia.robot.commandlib.utils.SystemUtil
import com.renhejia.robot.gesturefactory.manager.GestureCenter
import com.renhejia.robot.gesturefactory.parser.GestureData
import com.renhejia.robot.letianpaiservice.ILetianpaiService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class AutoService : Service() {
    private var iLetianpaiService: ILetianpaiService? = null
    private val gson: Gson = Gson()
    private var isServiceDestroy = false
    private val mHour = -1
    private val mMinute = 0
    private var handler: ChangeGestureHandler? = null
    private var currentGestureStage = RGestureConsts.GESTURE_CHANGE_STANDBY
    private var currentMode = ""

    /**
     * 是有找人的结果
     */
    private var hasSearchPeopleResult = false
    private var loopCount = 0

    /**
     * 找人的次数
     */
    private var searchPeopleCount = 0
    private val goSleepTime = 5 * 60 * 1000
    private val goSleepMode = Runnable {
        LogUtils.logd("AutoService", "run: 跳到睡眠模式")
        if (iLetianpaiService != null && SystemUtil.get("robot_not_sleep", "").isEmpty()) {
            try {
                iLetianpaiService!!.setAppCmd(
                    AppCmdConsts.COMMAND_VALUE_TO_SLEEP_MODE,
                    AppCmdConsts.COMMAND_VALUE_TO_SLEEP_MODE
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    private val ltpAppCmdCallback: LtpAppCmdCallback.Stub = object : LtpAppCmdCallback.Stub() {
        override fun onAppCommandReceived(command: String, data: String) {
            LogUtils.logd("AutoService", "onAppCommandReceived: $command    $data")
            try {
                when (command) {
                    RobotRemoteConsts.LOCAL_COMMAND_VALUE_IDENT_FACE_RESULT -> {
                        searchPeopleResult(data)
                    }
                    "killProcess" -> if (data != null && (data.contains("com.geeui.face") || data.contains(
                            "all"
                        )) && faceChangeListener != null
                    ) {
                        faceChangeListener!!.finishProcess()
                        showGestures(ArrayList(), -1)
                    }
                    "com.geeui.face" -> if (data == RobotRemoteConsts.COMMAND_VALUE_EXIT) {
                        stopAuoMode()
                    } else if (data != currentMode) {
                        LogUtils.logd(
                            "AutoService",
                            "onAppCommandReceived:currentMode: $currentMode   data: $data"
                        )
                        if (currentMode == RobotRemoteConsts.COMMAND_VALUE_CHANGE_MODE_ROBOT) {
                            stopAuoMode()
                        }
                        if (data == RobotRemoteConsts.COMMAND_VALUE_CHANGE_MODE_ROBOT) {
                            currentIndex = 0
                            startAutoMode()
                        }
                        if (data == RobotRemoteConsts.COMMAND_VALUE_CHANGE_MODE_SLEEP) {
                            iLetianpaiService!!.setMcuCommand(
                                MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL,
                                PowerMotion(3, 0).toString()
                            )
                            LogUtils.logd("AutoService", "onAppCommandReceived: 收到卸力执行30500000")
                            iLetianpaiService!!.setMcuCommand(
                                MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL,
                                PowerMotion(5, 0).toString()
                            )
                            LogUtils.logd("AutoService", "onAppCommandReceived: 收到卸力执行3050")
                        } else {
                            iLetianpaiService!!.setMcuCommand(
                                MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL,
                                PowerMotion(3, 1).toString()
                            )
                            LogUtils.logd("AutoService", "onAppCommandReceived: 收到上力执行3151-0000")
                            iLetianpaiService!!.setMcuCommand(
                                MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL,
                                PowerMotion(5, 1).toString()
                            )
                            LogUtils.logd("AutoService", "onAppCommandReceived: 收到上力执行3151")
                        }
                        currentMode = data
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val ltpExpressionCallback: LtpExpressionCallback =
        object : LtpExpressionCallback.Stub() {
            override fun onExpressionChanged(command: String, data: String) {
                LogUtils.logd("AutoService", "收到AIDL切换表情: $command   $data")
                if (!data.isEmpty() && faceChangeListener != null) {
                    if (data.startsWith("{")) {
                        val face = gson!!.fromJson(data, Face::class.java)
                        if (!face.face.isEmpty()) {
                            faceChangeListener!!.changeFace(face.face)
                        }
                    } else {
                        faceChangeListener!!.changeFace(data)
                    }
                }
            }
        }
    private val ltpLongConnectCallback: LtpLongConnectCallback =
        object : LtpLongConnectCallback.Stub() {
            override fun onLongConnectCommand(command: String, data: String) {
                LogUtils.logd("AutoService", "onLongConnectCommand: $command  data:$data")
                if ("selfIntroduction" == command) {
                    startSelfntroduction()
                } else if ("deviceRemoteMsgPush" == command && "remoteStroll" == data) {
                    getRemoteStrollGesture()
                }
            }
        }

    private fun getRemoteStrollGesture() {
        GlobalScope.launch {
            GeeUiNetManager.get(this@AutoService,
                "/robot_api/v1/common/getConfig?config_key=remote_stroll",
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            var json = response.body?.string()
                            var jo = JSONObject(json)
                            var data = jo.optJSONObject("data")
                            var ad = data.optJSONArray("config_data")
                            var type = object : TypeToken<ArrayList<GestureData>>() {}.type
                            val list = gson?.fromJson<ArrayList<GestureData>>(ad.toString(), type)
                            if (!list.isNullOrEmpty()) {
                                showGestures(list, RGestureConsts.GESTURE_REMOTESTROLL)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                })
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            iLetianpaiService = ILetianpaiService.Stub.asInterface(service)
            try {
                iLetianpaiService?.let {
                    it.registerAppCmdCallback(ltpAppCmdCallback)
                    it.registerExpressionCallback(ltpExpressionCallback)
                    it.registerLCCallback(ltpLongConnectCallback)
                    if (currentMode != RobotRemoteConsts.COMMAND_VALUE_CHANGE_MODE_SLEEP) {
                        it.setMcuCommand(
                            MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL,
                            PowerMotion(3, 1).toString()
                        )
                        it.setMcuCommand(
                            MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL,
                            PowerMotion(5, 1).toString()
                        )
                        LogUtils.logd("AutoService", "onAppCommandReceived: 启动执行上力3151")
                    } else {
                        it.setMcuCommand(
                            MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL,
                            PowerMotion(3, 0).toString()
                        )
                        it.setMcuCommand(
                            MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL,
                            PowerMotion(5, 0).toString()
                        )
                        LogUtils.logd("AutoService", "onAppCommandReceived: 启动执行卸力3050")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            iLetianpaiService = null
        }
    }

    @Volatile
    private var faceServiceRuning = false


    private var currentIndex = 0
    override fun onCreate() {
        super.onCreate()
        init()
    }

    private fun init() {
        handler = ChangeGestureHandler(this@AutoService)
        addGestureListeners()
        connectService()
    }

    /**
     * 自我介绍
     */
    private fun startSelfntroduction() {
        showGestures(GestureCenter.youPinGestures(), RGestureConsts.GESTURE_ID_SELF_NTRODUCTION)
    }

    fun startAutoMode() {
        LogUtils.logd("AutoService", "startAutoMode: ")
        val message = Message()
        message.what = RGestureConsts.GESTURE_NEW_ROBOT_POSE
        //        message.what = RGestureConsts.GESTURE_CHANGE_STANDBY;
        handler!!.handleMessage(message)
//        handler!!.postDelayed(goSleepMode, goSleepTime.toLong())
    }

    fun stopAuoMode() {
        LogUtils.logd("AutoService", "stopAuoMode: ")
        //终止当前的动作
        showGestures(ArrayList(), -1)
        currentMode = RobotRemoteConsts.COMMAND_VALUE_EXIT
        handler?.removeCallbacksAndMessages(null)
        closeFaceIdent()
    }

    //链接服务端
    private fun connectService() {
        val intent = Intent()
        intent.setPackage("com.renhejia.robot.letianpaiservice")
        intent.action = "android.intent.action.LETIANPAI"
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun addGestureListeners() {
        GestureCallback.getInstance().setGestureCompleteListener { gesture: String, taskId: Int ->
            Log.e("onGestureCompleted", "gesture 完成$gesture taskId: $taskId")
            if (isServiceDestroy) {
                return@setGestureCompleteListener
            }
            when (taskId) {
                RGestureConsts.GESTURE_ID_24_HOUR -> {}
                RGestureConsts.GESTURE_CHANGE_STANDBY, RGestureConsts.GESTURE_CHANGE_PEOPLE, RGestureConsts.GESTURE_CHANGE_CLASS_B, RGestureConsts.GESTURE_CHANGE_CLASS_C, RGestureConsts.GESTURE_CHANGE_CLASS_D, RGestureConsts.GESTURE_SEARCH_PEOPLE_RESULT, RGestureConsts.GESTURE_CHANGE_ALL -> onListGestureCompleted(
                    taskId
                )
                RGestureConsts.GESTURE_ID_SELF_NTRODUCTION -> {}
                RGestureConsts.GESTURE_NEW_ROBOT_POSE -> {
                    startAutoMode()
                }
                0x500 -> showRandomAll()
                else -> {}
            }
        }
    }

    var randomTime = Random()
    private fun onListGestureCompleted(taskId: Int) {
        LogUtils.logd(
            "AutoService",
            "onListGestureCompleted: 执行完成的gesture id:$taskId    currentMode $currentMode currentGestureStage: $currentGestureStage  isServiceDestroy:$isServiceDestroy"
        )
        if (isServiceDestroy) {
            return
        }
        //        if (!currentMode.equals("robot")) {
        if (currentGestureStage == RGestureConsts.GESTURE_SEARCH_PEOPLE_RESULT) {
            showNewRobotPose()
            return
        }
//        }
        var nextTaskId = 0
        var time = 0
        when (currentGestureStage) {
            RGestureConsts.GESTURE_CHANGE_STANDBY -> {
                nextTaskId = if (loopCount == 1) {
                    RGestureConsts.GESTURE_CHANGE_PEOPLE
                } else {
                    RGestureConsts.GESTURE_CHANGE_CLASS_B
                }
                hasSearchPeopleResult = false
                time = 0
            }
            RGestureConsts.GESTURE_CHANGE_PEOPLE -> {
                nextTaskId = RGestureConsts.GESTURE_CHANGE_PEOPLE
                time = 0
            }
            RGestureConsts.GESTURE_SEARCH_PEOPLE_RESULT -> {
                LogUtils.logd("AutoService", "onListGestureCompleted: $currentMode")
                if (currentMode == RobotRemoteConsts.COMMAND_VALUE_CHANGE_MODE_ROBOT) {
                    nextTaskId = RGestureConsts.GESTURE_CHANGE_CLASS_B
                    time = (randomTime.nextInt(2) + 1) * 1000
                } else {
                    nextTaskId = RGestureConsts.GESTURE_SEARCH_PEOPLE_RESULT_CLOSE
                    time = 0
                }
            }
            RGestureConsts.GESTURE_CHANGE_CLASS_B -> {
                nextTaskId = RGestureConsts.GESTURE_CHANGE_CLASS_C
                time = (randomTime.nextInt(2) + 1) * 1000
            }
            RGestureConsts.GESTURE_CHANGE_CLASS_C -> {
                nextTaskId = RGestureConsts.GESTURE_CHANGE_CLASS_D
                time = (randomTime.nextInt(2) + 1) * 1000
            }
            RGestureConsts.GESTURE_CHANGE_CLASS_D -> {
                nextTaskId = RGestureConsts.GESTURE_CHANGE_ALL
                time = (randomTime.nextInt(2) + 1) * 1000
            }
            RGestureConsts.GESTURE_CHANGE_ALL -> {
                nextTaskId = RGestureConsts.GESTURE_CHANGE_STANDBY
                time = (randomTime.nextInt(2) + 1) * 1000
            }
        }
        val finalNextTaskId = nextTaskId
        LogUtils.logd(
            "AutoService",
            "onListGestureCompleted: 延迟时间：$time     finalNextTaskId: $finalNextTaskId handler:$handler"
        )
        handler!!.postDelayed({
            if (handler != null) {
                val message = Message()
                message.what = finalNextTaskId
                handler!!.handleMessage(message)
            }
        }, time.toLong())
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceDestroy = true
        handler?.removeCallbacksAndMessages(null)
        handler = null
        closeFaceIdent()
        try {
//            iLetianpaiService.setMcuCommand(MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, new PowerMotion(3, 0).toString());
//            iLetianpaiService.setMcuCommand(MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, new PowerMotion(5, 0).toString());
            iLetianpaiService!!.unregisterAppCmdCallback(ltpAppCmdCallback)
            iLetianpaiService!!.unregisterExpressionCallback(ltpExpressionCallback)
            iLetianpaiService!!.unregisterLCCallback(ltpLongConnectCallback)
        } catch (e: Exception) {
            LogUtils.logd(TAG, "AutoService onDestroy: 解除绑定catch")
            e.printStackTrace()
        }
        unbindService(serviceConnection)
        LogUtils.logd("AutoService", "onDestroy: ")
    }

    private inner class ChangeGestureHandler(context: Context) : Handler() {
        private val context: WeakReference<Context>

        init {
            this.context = WeakReference(context)
        }

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (isServiceDestroy) {
                return
            }
            when (msg.what) {
                RGestureConsts.GESTURE_CHANGE_STANDBY -> changeStandbyStatus()
                RGestureConsts.GESTURE_CHANGE_PEOPLE -> changePeopleStatus()
                RGestureConsts.GESTURE_CHANGE_CLASS_B -> classBSecondStatus()
                RGestureConsts.GESTURE_CHANGE_CLASS_C -> changeClassC()
                RGestureConsts.GESTURE_CHANGE_CLASS_D -> changeClassD()
                RGestureConsts.GESTURE_CHANGE_ALL -> changeAllStatus()
                RGestureConsts.GESTURE_CHANGE_COMMON_DISPLAY -> changeToDisplayMode()
                RGestureConsts.GESTURE_SEARCH_PEOPLE_RESULT_CLOSE -> closeSearchPeople()
                RGestureConsts.GESTURE_NEW_ROBOT_POSE -> {
                    showNewRobotPose()
                }
                RGestureConsts.GESTURE_SEARCH_PEOPLE_START_DELAY_STOP -> {
                    searchPeopleResult("0")
                }
            }
        }
    }

    private fun showNewRobotPose() {
        val list = commonRobotGesture
        currentIndex++
        Log.i(TAG, "showNewRobotPose: $currentIndex  ${currentIndex % 5} ${gson?.toJson(list)}")
        if (currentIndex % 5 == 0) {
            openFaceIdent()
        } else {
            showGestures(list, RGestureConsts.GESTURE_NEW_ROBOT_POSE)
        }
    }

    private fun closeSearchPeople() {
        try {
            if (iLetianpaiService != null) {
                LogUtils.logd("AutoService", "closeSearchPeople: ")
                iLetianpaiService!!.setSpeechCmd(SpeechConst.COMMAND_SEARCH_PEOPLE, "0")
            }
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun changeToDisplayMode() {}

    /**
     * 默认姿态
     * A类行为
     */
    private fun changeStandbyStatus() {
        currentGestureStage = RGestureConsts.GESTURE_CHANGE_STANDBY
        LogUtils.logd("ChangeGestureHandler", "handleMessage: loopCount:")
        LogUtils.logd("ChangeGestureHandler", "将要执行 本次是第" + loopCount + "次===============")
        val list = ArrayList<GestureData>()
        val gestureData = GestureData()
        gestureData.expression = Face("h0063", "黄眼睛")
        gestureData.footAction = Motion(getRandomMotion(intArrayOf(18)))
        gestureData.soundEffects =
            Sound(getRandomString(arrayOf("a0134", "a0147", "a0108", "a0102", "a0098")))
        gestureData.interval = 2500
        list.add(gestureData)
        logGestureData("A类行为", list)
        showGestures(list, RGestureConsts.GESTURE_CHANGE_STANDBY)
        loopCount++
    }

    private fun changePeopleStatus() {
        searchPeopleCount++
        if (searchPeopleCount > SEARCH_MAX_COUNT) {
            searchPeopleResult("0")
        } else {
            searchPeopleGesture()
            if (!faceServiceRuning) {
                openFaceIdent()
            }
        }
    }

    private fun searchPeopleGesture() {
        val list = ArrayList<GestureData>()
        val gestureData = GestureData()
        gestureData.expression = Face(
            getRandomString(
                arrayOf(
                    "h0019", "h0024", "h0025", "h0021", "h0048", "h0049"
                )
            )
        )
        gestureData.soundEffects =
            Sound(getRandomString(arrayOf("a0129", "a0128", "a0127", "a0126")))
        gestureData.antennalight = randomAntennaLight
        gestureData.interval = 2500

//        GestureData gestureData1 = new GestureData();
//        gestureData1.setExpression(new Face(getRandomString(new String[]{"h0019", "h0024", "h0025", "h0021", "h0048", "h0049"})));
//        gestureData1.setAntennalight(getRandomAntennaLight());
//        gestureData1.setInterval(2000);
//
//        GestureData gestureData2 = new GestureData();
//        gestureData2.setExpression(new Face(getRandomString(new String[]{"h0019", "h0024", "h0025", "h0021", "h0048", "h0049"})));
//        gestureData2.setAntennalight(getRandomAntennaLight());
//        gestureData2.setInterval(2000);
        list.add(gestureData)
        //        list.add(gestureData1);
//        list.add(gestureData2);
        logGestureData("找人行为", list)
        showGestures(list, RGestureConsts.GESTURE_CHANGE_PEOPLE)
    }

    /**
     * 找人结果,执行一个姿态
     */
    private fun searchPeopleResult(data: String) {
        hasSearchPeopleResult = true
        searchPeopleCount = 0
        closeFaceIdent()
        currentGestureStage = RGestureConsts.GESTURE_SEARCH_PEOPLE_RESULT
        var hasOwner = false
        var hasPeople = false
        val faceType = object : TypeToken<List<IdentFaceModel>>() {}.type
        LogUtils.logd(TAG, "searchPeopleResult: $data   ${data=="0"}")
        if (data != "0") {
            val identFaceResultList = gson.fromJson<List<IdentFaceModel>>(data, faceType)
            identFaceResultList.forEach {
                if (it.faceNumber ?: 0 > 0) {
                    hasPeople = true
                    return@forEach
                }
                if (it.isOwner == true) {
                    hasOwner = true
                    return@forEach
                }
            }
        }

        val list: ArrayList<GestureData> = if (hasPeople) {
            GestureCenter.foundPeoGestureData()
        } else {
            GestureCenter.foundNoPeoGestureData()
        }
        LogUtils.logd("AutoService", "searchPeopleResult: 找到了人$data")
        //        list.addAll(getEGestureData());
//        list.addAll(getEGestureData());
        showGestures(list, RGestureConsts.GESTURE_SEARCH_PEOPLE_RESULT)
    }

    private fun classBSecondStatus() {
        currentGestureStage = RGestureConsts.GESTURE_CHANGE_CLASS_B
        val gestureData = GestureData()
        val list = classBGesture
        list.add(gestureData)
        logGestureData("B类行为", list)
        showGestures(list, RGestureConsts.GESTURE_CHANGE_CLASS_B)
    }

    /**
     * B 类行为
     *
     * @return
     */
    private val classBGesture: ArrayList<GestureData>
        private get() {
            val index = randomTime.nextInt(5)

            /*switch (index) {
            case 0:
                LogUtils.logd("AutoService", "getOneDaijiPose: pairGestureData");
                return GestureCenter.pairGestureData();
            case 1:
                LogUtils.logd("AutoService", "getOneDaijiPose: startChargingGestureData");
                return GestureCenter.startChargingGestureData();
            case 2:
                LogUtils.logd("AutoService", "getOneDaijiPose: standByGestureData");
                return GestureCenter.standByGestureData();
            case 3:
                LogUtils.logd("AutoService", "getOneDaijiPose: dodgeGestureData");
                return GestureCenter.dodgeGestureData();
            case 4:
                LogUtils.logd("AutoService", "getOneDaijiPose: sleepyGestureData");
                return GestureCenter.sleepyGestureData();
            default:
                LogUtils.logd("AutoService", "getOneDaijiPose: startChargingGestureData");
                return GestureCenter.startChargingGestureData();
        }*/
            val list = ArrayList<GestureData>()
            val gestureData = GestureData()
            gestureData.expression = Face("h0027")
            gestureData.footAction = Motion(null, 63, 3)
            gestureData.soundEffects = Sound("a0024")
            gestureData.antennalight = randomAntennaLight
            gestureData.interval = 6000
            val gestureData1 = GestureData()
            gestureData1.expression = Face("h0028")
            gestureData1.footAction = Motion(null, 64, 3)
            gestureData1.soundEffects = Sound("a0025")
            gestureData1.antennalight = randomAntennaLight
            gestureData1.interval = 6000
            val gestureData2 = GestureData()
            gestureData2.expression = Face("h0029")
            gestureData2.footAction = Motion(null, 5, 3)
            gestureData2.soundEffects = Sound("a0025")
            gestureData2.antennalight = randomAntennaLight
            gestureData2.interval = 6000
            val gestureData3 = GestureData()
            gestureData3.expression = Face("h0030")
            gestureData3.footAction = Motion(null, 6, 3)
            gestureData3.soundEffects = Sound("a0026")
            gestureData3.antennalight = randomAntennaLight
            gestureData3.interval = 6000
            list.add(gestureData)
            list.add(gestureData1)
            list.add(gestureData2)
            list.add(gestureData3)
            return list
        }

    private fun changeClassC() {
        currentGestureStage = RGestureConsts.GESTURE_CHANGE_CLASS_C
        val list = classCGestue
        logGestureData("C类行为", list)
        showGestures(list, RGestureConsts.GESTURE_CHANGE_CLASS_C)
    }

    private val classCGestue: ArrayList<GestureData>
        get() {
            val list = ArrayList<GestureData>()
            val gestureData = GestureData()
            gestureData.expression = Face(
                getRandomString(
                    arrayOf(
                        "h0007", "h0043", "h0044", "h0042", "h0026", "h0015", "h0041", "h0030"
                    )
                )
            )
            gestureData.footAction = Motion(
                getRandomMotion(
                    intArrayOf(
                        11, 12, 28, 58, 34, 43, 48, 51, 52
                    )
                )
            )
            gestureData.soundEffects = Sound(
                getRandomString(
                    arrayOf(
                        "a0092", "a0098", "a0049", "a0126", "a0127", "a0023", "a0024"
                    )
                )
            )
            gestureData.antennalight = randomAntennaLight
            gestureData.interval = 2500
            list.add(gestureData)
            return list
        }

    private fun changeClassD() {
        currentGestureStage = RGestureConsts.GESTURE_CHANGE_CLASS_D
        val list = classDGesture
        logGestureData("D类行为", list)
        showGestures(list, RGestureConsts.GESTURE_CHANGE_CLASS_D)
    }

    private val classDGesture: ArrayList<GestureData>
        get() {
            val list = ArrayList<GestureData>()
            val count = getRandomIndex(3) + 2
            for (i in 0 until count) {
                val gestureData = GestureData()
                gestureData.expression = Face(
                    getRandomString(
                        arrayOf(
                            "h0030", "h0031", "h0043", "h0045", "h0057"
                        )
                    )
                )
                gestureData.footAction = Motion(getRandomMotion(intArrayOf(63, 64, 27)))
                gestureData.earAction = AntennaMotion(getRandomMotion(intArrayOf(1, 2)))
                gestureData.antennalight = randomAntennaLight
                gestureData.interval = 3000
                list.add(gestureData)
            }
            LogUtils.logd("AutoService", "次数：$count")
            return list
        }

    private fun changeAllStatus() {
        currentGestureStage = RGestureConsts.GESTURE_CHANGE_ALL
        //        ArrayList<GestureData> list = getOneFixedPose();
        val list = getAllStatusGesture()
        logGestureData("所有姿态库随机其中一个", list)
        showGestures(list, RGestureConsts.GESTURE_CHANGE_ALL)
    }

    private fun getAllStatusGesture(): java.util.ArrayList<GestureData> {
        val list = GestureCenter.getRandomGesture()
        return list
    }

    private val eGestureData: ArrayList<GestureData>
        private get() {
            val list = ArrayList<GestureData>()
            val gestureData = GestureData()
            gestureData.expression = Face(
                getRandomString(
                    arrayOf(
                        "h0015", "h0019", "h0036", "h0048"
                    )
                )
            )
            gestureData.footAction = Motion(
                getRandomMotion(
                    intArrayOf(
                        13, 14, 15, 16, 17, 44, 45, 58, 59, 66
                    )
                )
            )
            gestureData.soundEffects =
                Sound(getRandomString(arrayOf("a0085", "a0065", "a0069", "a0070", "a0071")))
            gestureData.interval = 3000
            list.add(gestureData)
            return list
        }

    fun showGestures(list: ArrayList<GestureData>, taskId: Int) {
        GestureDataThreadExecutor.getInstance().execute {
            LogUtils.logd("AutoService", "run start: taskId:$taskId")
            for (gestureData in list) {
                responseGestureData(gestureData, iLetianpaiService)
                try {
                    if (gestureData.interval == 0L) {
                        Thread.sleep(2000)
                    } else {
                        Thread.sleep(gestureData.interval)
                    }
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
            }
            LogUtils.logd("AutoService", "run end: taskId:$taskId")
            GestureCallback.getInstance().setGesturesComplete("list", taskId)
        }
    }

    private fun responseGestureData(
        gestureData: GestureData?, iLetianpaiService: ILetianpaiService?
    ) {
        logGestureData(gestureData)
        if (gestureData == null) {
            return
        }
        try {
            if (gestureData.ttsInfo != null) {
                //响应单元在Launcher
//                RhjAudioManager.getInstance().speak(gestureData.getTtsInfo().getTts());
                iLetianpaiService?.setTTS("speakText", gestureData.ttsInfo.tts)
            }
            if (gestureData.expression != null) {
                if (faceChangeListener != null) {
                    faceChangeListener!!.changeFace(gestureData.expression.face)
                }
            }

            if (gestureData.soundEffects != null) {
                iLetianpaiService?.setAudioEffect(
                    RobotRemoteConsts.COMMAND_TYPE_SOUND, gestureData.soundEffects.sound
                )
            }
            if (gestureData.footAction != null) {
                iLetianpaiService?.setMcuCommand(
                    RobotRemoteConsts.COMMAND_TYPE_MOTION, gestureData.footAction.toString()
                )
            }

            if (gestureData.earAction != null) {
                if (iLetianpaiService != null && getRandomIndex(20) % 6 == 0) {
                    iLetianpaiService.setMcuCommand(
                        RobotRemoteConsts.COMMAND_TYPE_ANTENNA_MOTION,
                        gestureData.earAction.toString()
                    )
                }
            }

            if (gestureData.antennalight != null) {
                iLetianpaiService?.setMcuCommand(
                    RobotRemoteConsts.COMMAND_TYPE_ANTENNA_LIGHT,
                    gestureData.antennalight.toString()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun logGestureData(gestureData: GestureData?) {
        LogUtils.logd("AutoService", "解析给实际执行单元 $gestureData")
    }

    private fun getRandomIndex(length: Int): Int {
        val r = Random()
        return r.nextInt(length)
    }

    private fun getRandomString(group: Array<String>): String {
        val r = Random()
        return group[r.nextInt(group.size)]
    }

    private fun getRandomMotion(group: IntArray): Int {
        val r = Random()
        return group[r.nextInt(group.size)]
    }

    private val randomAntennaLight: AntennaLight
        private get() {
            val r = Random()
            return AntennaLight("on", getRandomIndex(9) + 1)
        }
    private val randomAntennaMotion: AntennaMotion
        private get() = AntennaMotion(getRandomIndex(3) + 1)

    /**
     * 打开人脸识别
     */
    private fun openFaceIdent() {
        LogUtils.logd("AutoService", "openFaceIdent: ")
        val intent = Intent().apply {
            component = ComponentName("com.ltp.ident", "com.ltp.ident.services.IdentFaceService")
            putExtra("identNeedOwenr", false)
            putExtra("identAutoStop", true)
            putExtra("motionNumber", 21)
            putExtra("hasMotion", true)
            putExtra("identMaxCount", 20)
            putExtra("identThreshold", 0.32)
        }
        faceServiceRuning = true
        startService(intent)
        handler?.sendEmptyMessageDelayed(
            RGestureConsts.GESTURE_SEARCH_PEOPLE_START_DELAY_STOP,
            45 * 1000
        )
    }

    private fun closeFaceIdent() {
        LogUtils.logd(TAG, "closeFaceIdent: $faceServiceRuning")
        faceServiceRuning = false
        iLetianpaiService?.setAppCmd("killProcess", "com.ltp.ident")
        handler?.removeMessages(RGestureConsts.GESTURE_SEARCH_PEOPLE_START_DELAY_STOP)
    }

    private fun logGestureData(tag: String, list: ArrayList<GestureData>) {
        var log = ""
        for (i in list.indices) {
            val gestureData = list[i]
            if (gestureData.footAction != null) {
                log += "   动作:" + gestureData.footAction.showLog()
            }
            if (gestureData.expression != null) {
                log += "   表情：" + gestureData.expression.showLog()
            }
            if (gestureData.soundEffects != null) {
                log += "   声音：" + gestureData.soundEffects.showLog()
            }
            if (gestureData.earAction != null) {
                log += "   耳朵：" + gestureData.earAction
            }
            if (gestureData.antennalight != null) {
                log += "    天线" + gestureData.antennalight
            }
            if (i > 0) {
                log += "\n              "
            }
        }
        LogUtils.logd("AudioCmdResponseManager", "将要执行 $tag  $log")
    }

    private fun showRandomAll() {
        showGestures(GestureCenter.getAllRandom(), 0x500)
    }

    fun setRobotMode(mode: String) {
        LogUtils.logd("AutoService", "setRobotMode: 机器人模式改变$mode")
        if (currentMode == mode) {
            return
        } else {
            currentMode = mode
            when (mode) {
                RobotRemoteConsts.COMMAND_VALUE_CHANGE_MODE_ROBOT -> {
                    currentIndex = 0
                    startAutoMode()
                }
                RobotRemoteConsts.COMMAND_VALUE_CHANGE_MODE_STATIC -> {}
                RobotRemoteConsts.COMMAND_VALUE_CHANGE_MODE_DEMO -> {}
                RobotRemoteConsts.COMMAND_VALUE_CHANGE_MODE_SLEEP -> LogUtils.logd(
                    "AutoService", "当前是机器人睡眠状态"
                )
            }
        }
    }

    private val binder: IBinder = MyBinder()
    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    inner class MyBinder : Binder() {
        val service: AutoService
            get() = this@AutoService
    }

    private var faceChangeListener: OnFaceChangeListener? = null
    fun setFaceChangeListener(faceChangeListener: OnFaceChangeListener?) {
        this.faceChangeListener = faceChangeListener
    }

    interface OnFaceChangeListener {
        fun changeFace(faceName: String?)
        fun finishProcess()
    }

    val commonRobotGesture: ArrayList<GestureData>
        get() {
            val list = ArrayList<GestureData>()
            val hashMap = HashMap<Int, Array<GestureData>>()
            hashMap[0] = gestureData
            hashMap[1] = gWorkForward
            hashMap[2] = gWorkForwardLongTime
            hashMap[3] = gWorkForwardSmile
            hashMap[4] = gCommonUpDown
            hashMap[5] = gCommonBack
            hashMap[6] = gCommonTurnLeft
            hashMap[7] = gCommonTurnRight
            hashMap[8] = gCommonPangXie
            hashMap[9] = gCommonDouJiao

            hashMap[getRandomIndex(10)]?.let { list.addAll(it.toList()) }
            //            hashMap[0]?.let { list.addAll(it.toList()) }
            //            hashMap[1]?.let { list.addAll(it.toList()) }
            //            hashMap[2]?.let { list.addAll(it.toList()) }
            //            hashMap[3]?.let { list.addAll(it.toList()) }
            //            hashMap[4]?.let { list.addAll(it.toList()) }
            //            hashMap[5]?.let { list.addAll(it.toList()) }
            //            hashMap[6]?.let { list.addAll(it.toList()) }
            //            hashMap[7]?.let { list.addAll(it.toList()) }
            //            hashMap[8]?.let { list.addAll(it.toList()) }
            //            hashMap[9]?.let { list.addAll(it.toList()) }
            //            hashMap[5]?.let { list.addAll(it.toList()) }
            //            hashMap[0]?.let { list.addAll(it.toList()) }
            return list
        }
    val gestureData = arrayOf(GestureData().apply {
        expression = Face(
            getRandomString(
                arrayOf(
                    "h0057",
                    "h0058",
                    "h0059",
                    "h0060",
                    "h0061",
                    "h0135",
                    "h0136",
                    "h0137",
                    "h0138",
                    "h0139",
                    "h0140",
                    "h0141",
                    "h0142",
                    "h0143",
                    "h0144",
                    "h0145",
                    "h0146",
                    "h0147",
                    "h0148",
                    "h0149",
                    "h0150",
                    "h0151",
                    "h0152",
                    "h0153",
                    "h0154",
                    "h0155",
                    "h0156",
                    "h0157",
                    "h0158",
                    "h0159",
                    "h0160",
                    "h0161",
                    "h0162",
                    "h0163",
                    "h0164",
                    "h0165",
                    "h0166",
                    "h0167",
                    "h0168",
                    "h0169",
                    "h0170",
                    "h0171",
                    "h0172",
                    "h0173",
                    "h0174",
                    "h0175",
                    "h0176"
                )
            )
        )
        interval = (getRandomIndex(20) + 10) * 1000L
    })
    val gWorkForward = arrayOf(GestureData().apply {
        expression = Face(getRandomString(arrayOf("h0157", "h0154")))
        footAction = Motion(1, 2, 3)
        interval = 2 * 2000
    })
    val gWorkForwardLongTime = arrayOf(GestureData().apply {
        expression = Face(getRandomString(arrayOf("h0157", "h0154")))
        footAction = Motion(1, 5, 3)
        interval = 4 * 2000
    })
    val gWorkForwardSmile = arrayOf(GestureData().apply {
        expression = Face("h0006")
        interval = 1000
    }, GestureData().apply {
        footAction = Motion(1, 6, 3)
        interval = 6 * 1000
    })
    val gCommonUpDown = arrayOf(GestureData().apply {
        expression = Face("h0129")
        interval = 500
    }, GestureData().apply {
        footAction = Motion(17, 4, 2)
        interval = 2000
    })
    val gCommonBack = arrayOf(GestureData().apply {
        expression = Face("h0021")
        interval = 1000
    }, GestureData().apply {
        footAction = Motion(2, 2, 3)
        interval = 4 * 1000
    })
    val gCommonTurnLeft = arrayOf(GestureData().apply {
        expression = Face("h0126")
        footAction = Motion(21, 4, 3)
        interval = 2 * 2000
    })
    val gCommonTurnRight = arrayOf(GestureData().apply {
        expression = Face("h0125")
        footAction = Motion(22, 4, 3)
        interval = 2 * 2000
    })

    val gCommonPangXie = arrayOf(GestureData().apply {
        expression = Face("h0179")
        interval = 400
    }, GestureData().apply {
        footAction = Motion(5, 1, 3)
        interval = 1 * 2000
    }, GestureData().apply {
        footAction = Motion(6, 1, 3)
        interval = 1 * 2000
    })
    val gCommonDouJiao = arrayOf(GestureData().apply {
        expression = Face("h0179")
        footAction = Motion(9, 2, 1)
        interval = 2 * 1000
    }, GestureData().apply {
        footAction = Motion(10, 2, 1)
        interval = 2 * 1000
    })
    /*val commonRobotGesture: ArrayList<GestureData>
        get() {
            val list = ArrayList<GestureData>()
            val hashMap = HashMap<Int, Array<GestureData>>()
            hashMap[0] = gestureData
            hashMap[1] = gWorkForward
            hashMap[2] = gWorkForwardLongTime
            hashMap[3] = gWorkForwardSmile
            hashMap[4] = gCommonUpDown
            hashMap[5] = gCommonBack
            hashMap[6] = gCommonTurnLeft
            hashMap[7] = gCommonTurnRight
            hashMap[8] = gCommonPangXie
            hashMap[9] = gCommonDouJiao

            hashMap[getRandomIndex(10)]?.let { list.addAll(it.toList()) }
//            hashMap[0]?.let { list.addAll(it.toList()) }
//            hashMap[1]?.let { list.addAll(it.toList()) }
//            hashMap[2]?.let { list.addAll(it.toList()) }
//            hashMap[3]?.let { list.addAll(it.toList()) }
//            hashMap[4]?.let { list.addAll(it.toList()) }
//            hashMap[5]?.let { list.addAll(it.toList()) }
//            hashMap[6]?.let { list.addAll(it.toList()) }
//            hashMap[7]?.let { list.addAll(it.toList()) }
//            hashMap[8]?.let { list.addAll(it.toList()) }
//            hashMap[9]?.let { list.addAll(it.toList()) }
//            hashMap[5]?.let { list.addAll(it.toList()) }
//            hashMap[0]?.let { list.addAll(it.toList()) }
            return list
        }
    val gestureData = arrayOf(GestureData().apply {
        expression = Face(
            getRandomString(
                arrayOf(
                    "h0057",
                    "h0058",
                    "h0059",
                    "h0060",
                    "h0061",
                    "h0135",
                    "h0136",
                    "h0137",
                    "h0138",
                    "h0139",
                    "h0140",
                    "h0141",
                    "h0142",
                    "h0143",
                    "h0144",
                    "h0145",
                    "h0146",
                    "h0147",
                    "h0148",
                    "h0149",
                    "h0150",
                    "h0151",
                    "h0152",
                    "h0153",
                    "h0154",
                    "h0155",
                    "h0156",
                    "h0157",
                    "h0158",
                    "h0159",
                    "h0160",
                    "h0161",
                    "h0162",
                    "h0163",
                    "h0164",
                    "h0165",
                    "h0166",
                    "h0167",
                    "h0168",
                    "h0169",
                    "h0170",
                    "h0171",
                    "h0172",
                    "h0173",
                    "h0174",
                    "h0175",
                    "h0176"
                )
            )
        )
        interval = (getRandomIndex(20) + 10) * 1000L
    })
    val gWorkForward = arrayOf(GestureData().apply {
        expression = Face(getRandomString(arrayOf("h0157", "h0154")))
        footAction = Motion(63, 2, 2)
        interval = 2 * 2000
    })
    val gWorkForwardLongTime = arrayOf(GestureData().apply {
        expression = Face(getRandomString(arrayOf("h0157", "h0154")))
        footAction = Motion(63, 5, 2)
        interval = 4 * 2000
    })
    val gWorkForwardSmile = arrayOf(GestureData().apply {
        expression = Face("h0006")
        interval = 1000
    }, GestureData().apply {
        footAction = Motion(63, 6, 2)
        interval = 6 * 1000
    })
    val gCommonUpDown = arrayOf(GestureData().apply {
        expression = Face("h0129")
        interval = 500
    }, GestureData().apply {
        footAction = Motion(17, 4, 2)
        interval = 2000
    })
    val gCommonBack = arrayOf(GestureData().apply {
        expression = Face("h0021")
        interval = 1000
    }, GestureData().apply {
        footAction = Motion(2, 2, 3)
        interval = 4 * 1000
    })
    val gCommonTurnLeft = arrayOf(GestureData().apply {
        expression = Face("h0126")
        footAction = Motion(21, 1, 3)
        interval = 1500
    })
    val gCommonTurnRight = arrayOf(GestureData().apply {
        expression = Face("h0125")
        footAction = Motion(22, 1, 3)
        interval = 1500
    })

    val gCommonPangXie = arrayOf(GestureData().apply {
        expression = Face("h0179")
        interval = 400
    }, GestureData().apply {
        footAction = Motion(5, 1, 3)
        interval = 1 * 2000
    }, GestureData().apply {
        footAction = Motion(6, 1, 3)
        interval = 1 * 2000
    })
    val gCommonDouJiao = arrayOf(GestureData().apply {
        expression = Face("h0179")
        footAction = Motion(9, 2, 1)
        interval = 2 * 1000
    }, GestureData().apply {
        footAction = Motion(10, 2, 1)
        interval = 2 * 1000
    })*/

    companion object {
        private const val TAG = "DispatchService"
        const val SEARCH_MAX_COUNT = 30
    }
}