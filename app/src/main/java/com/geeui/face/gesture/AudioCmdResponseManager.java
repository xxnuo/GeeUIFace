package com.geeui.face.gesture;

import android.content.Context;
import android.os.RemoteException;

import com.renhejia.robot.commandlib.consts.RobotRemoteConsts;
import com.renhejia.robot.commandlib.consts.RobotRemoteConsts;
import com.renhejia.robot.commandlib.log.LogUtils;
import com.renhejia.robot.commandlib.parser.motion.Motion;
import com.renhejia.robot.gesturefactory.manager.GestureManager;
import com.renhejia.robot.gesturefactory.parser.GestureData;
import com.renhejia.robot.letianpaiservice.ILetianpaiService;

import java.util.ArrayList;

/**
 * 语音命令执行单元
 *
 * @author liujunbin
 */
public class AudioCmdResponseManager {

    private static AudioCmdResponseManager instance;
    private Context mContext;

    private AudioCmdResponseManager(Context context) {
        init(context);
    }

    private void init(Context context) {
        this.mContext = context;
    }

    public static AudioCmdResponseManager getInstance(Context context) {
        synchronized (AudioCmdResponseManager.class) {
            if (instance == null) {
                instance = new AudioCmdResponseManager(context.getApplicationContext());
            }
            return instance;
        }
    }

    public void responseGestures(ArrayList<GestureData> list, int taskId, ILetianpaiService iLetianpaiService) {
        GestureDataThreadExecutor.getInstance().execute(() -> {
            LogUtils.logd("AudioCmdResponseManager", "run start: taskId:" + taskId);
            for (GestureData gestureData : list) {

                responseGestureData(gestureData, iLetianpaiService);
                try {
                    if (gestureData.getInterval() == 0) {
                        Thread.sleep(2000);
                    } else {
                        Thread.sleep(gestureData.getInterval());
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            LogUtils.logd("AudioCmdResponseManager", "run end: taskId:" + taskId);
            GestureCallback.getInstance().setGesturesComplete("list", taskId);
        });
    }

    public static void responseGestureData(GestureData gestureData, ILetianpaiService iLetianpaiService) {

        logGestureData(gestureData);
        if (gestureData == null) {
            return;
        }
        try {
            if (gestureData.getTtsInfo() != null) {
                //响应单元在Launcher
//                RhjAudioManager.getInstance().speak(gestureData.getTtsInfo().getTts());
                iLetianpaiService.setTTS("speakText", gestureData.getTtsInfo().getTts());
            }
            if (gestureData.getExpression() != null) {
                //响应单元在Launcher
//                iLetianpaiService.setCommand(new LtpCommand(RobotRemoteConsts.COMMAND_TYPE_FACE, ((gestureData.getExpression()).toString())));
                iLetianpaiService.setExpression(RobotRemoteConsts.COMMAND_TYPE_FACE, (gestureData.getExpression()).toString());
            }
            if (gestureData.getAntennalight() != null) {
                //响应单元在MCUservice
//                iLetianpaiService.setCommand(new LtpCommand(RobotRemoteConsts.COMMAND_TYPE_ANTENNA_LIGHT, ((gestureData.getAntennalight()).toString())));
                iLetianpaiService.setMcuCommand(RobotRemoteConsts.COMMAND_TYPE_ANTENNA_LIGHT, (gestureData.getAntennalight()).toString());
            }
            if (gestureData.getSoundEffects() != null) {
                //响应单元在AudioService
//                iLetianpaiService.setCommand(new LtpCommand(RobotRemoteConsts.COMMAND_TYPE_SOUND, ((gestureData.getSoundEffects()).toString())));
                iLetianpaiService.setAudioEffect(RobotRemoteConsts.COMMAND_TYPE_SOUND, (gestureData.getSoundEffects()).toString());
            }
            if (gestureData.getFootAction() != null) {
                //响应单元在MCUservice
//                iLetianpaiService.setCommand(new LtpCommand(RobotRemoteConsts.COMMAND_TYPE_MOTION, (gestureData.getFootAction()).toString()));
                iLetianpaiService.setMcuCommand(RobotRemoteConsts.COMMAND_TYPE_MOTION, (gestureData.getFootAction()).toString());
            } else {
                Motion motion = new Motion();
                //0会立即停止当前的动作
                motion.setNumber(0);
//                iLetianpaiService.setCommand(new LtpCommand(RobotRemoteConsts.COMMAND_TYPE_MOTION, motion.toString()));
                iLetianpaiService.setMcuCommand(RobotRemoteConsts.COMMAND_TYPE_MOTION, motion.toString());
            }
            if (gestureData.getEarAction() != null) {
                //响应单元在MCUservice
//                iLetianpaiService.setCommand(new LtpCommand(RobotRemoteConsts.COMMAND_TYPE_ANTENNA_MOTION, (gestureData.getEarAction()).toString()));
                iLetianpaiService.setMcuCommand(RobotRemoteConsts.COMMAND_TYPE_ANTENNA_MOTION, (gestureData.getEarAction()).toString());
            } else {
                //天线
//                AntennaMotion antennaMotion=new AntennaMotion("sturn");
//                iLetianpaiService.setCommand(new LtpCommand(RobotRemoteConsts.COMMAND_TYPE_ANTENNA_MOTION, (gestureData.getEarAction()).toString()));
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private static void logGestureData(GestureData gestureData) {
//        String log = "";
//        if (gestureData.getFootAction() != null && !gestureData.getFootAction().getMotion().isEmpty()) {
//            log += "   动作:" + gestureData.getFootAction().getDesc();
//        }
//        if (gestureData.getExpression() != null && !gestureData.getExpression().getFace().isEmpty()) {
//            log += "   表情：" + gestureData.getExpression().getDesc();
//        }
//        if (gestureData.getSoundEffects() != null && !gestureData.getSoundEffects().getSound().isEmpty()) {
//            log += "   声音：" + gestureData.getSoundEffects().getDesc();
//        }
//        if (gestureData.getEarAction() != null && !gestureData.getEarAction().getAntenna_motion().isEmpty()) {
//            log += "   耳朵：" + gestureData.getEarAction().getAntenna_motion().isEmpty();
//        }
//
//        if (gestureData.getAntennalight() != null && !gestureData.getAntennalight().getAntenna_light().isEmpty()) {
//            log += "    天线" + gestureData.getAntennalight().getAntenna_light();
//        }
//
        LogUtils.logd("AudioCmdResponseManager", "解析给实际执行单元 " + gestureData);
    }


}
