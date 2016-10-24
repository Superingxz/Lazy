package com.github.lazylibrary.util;


import com.github.lazylibrary.baseapp.AppConfig;
import com.orhanobut.logger.LogLevel;
import com.orhanobut.logger.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * 如果用于android平台，将信息记录到“LogCat”。如果用于java平台，将信息记录到“Console”
 * 使用logger封装
 */
public class LogUtils {
    public static boolean DEBUG_ENABLE =false;// 是否调试模式
    /**
     * 在application调用初始化
     */
    public static void logInit(boolean debug) {
        DEBUG_ENABLE=debug;
        if (DEBUG_ENABLE) {
            Logger.init(AppConfig.DEBUG_TAG)                 // default PRETTYLOGGER or use just init()
                    .methodCount(2)                 // default 2
                    .logLevel(LogLevel.FULL)        // default LogLevel.FULL
                    .methodOffset(0);                // default 0
        } else {
            Logger.init()                 // default PRETTYLOGGER or use just init()
                    .methodCount(3)                 // default 2
                    .hideThreadInfo()               // default shown
                    .logLevel(LogLevel.NONE)        // default LogLevel.FULL
                    .methodOffset(2);
        }
    }
    public static void logd(String tag,String message) {
        if (DEBUG_ENABLE) {
            Logger.d(tag,message);
        }
    }
    public static void logd(String message) {
        if (DEBUG_ENABLE) {
            Logger.d(message);
        }
    }
    public static void loge(Throwable throwable, String message, Object... args) {
        if (DEBUG_ENABLE) {
            Logger.e(throwable, message, args);
        }
    }

    public static void loge(String message, Object... args) {
        if (DEBUG_ENABLE) {
            Logger.e(message, args);
        }
    }

    public static void logi(String message, Object... args) {
        if (DEBUG_ENABLE) {
            Logger.i(message, args);
        }
    }
    public static void logv(String message, Object... args) {
        if (DEBUG_ENABLE) {
            Logger.v(message, args);
        }
    }
    public static void logw(String message, Object... args) {
        if (DEBUG_ENABLE) {
            Logger.v(message, args);
        }
    }
    public static void logwtf(String message, Object... args) {
        if (DEBUG_ENABLE) {
            Logger.wtf(message, args);
        }
    }

    public static void logjson(String message) {
        if (DEBUG_ENABLE) {
            Logger.json(message);
        }
    }
    public static void logxml(String message) {
        if (DEBUG_ENABLE) {
            Logger.xml(message);
        }
    }

      /* ========================下面的是本地存储相关的========================== */
    /**
     * 写日志对象
     */
    private LogWriter logWriter;

    /**
     * 写入本地日志线程
     */
    private class LogWriter extends Thread {
        /**
         * 文件路径
         */
        private String mFilePath;
        /**
         * 调用这个类的线程
         */
        private int mPid;
        /**
         * 线程运行标志
         */
        private boolean isRunning = true;

        /**
         * @param filePath 文件路径
         * @param pid
         */
        public LogWriter(String filePath, int pid) {
            this.mPid = pid;
            this.mFilePath = filePath;
        }

        @Override
        public void run() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA);//日期格式化对象
            Process process = null;//进程
            BufferedReader reader = null;
            FileWriter writer = null;
            try {
                //执行命令行
                String cmd = "logcat *:e *:w | grep";
                process = Runtime.getRuntime().exec(cmd);
                //得到输入流
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()), 1024);
                //创建文件
                File file = new File(mFilePath);
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
                writer = new FileWriter(file, true);
                //循环写入文件
                String line = null;
                while (isRunning) {
                    line = reader.readLine();
                    if (line != null && line.length() > 0) {
                        writer.append("PID:" + this.mPid + "\t"
                                + sdf.format(new Date(System.currentTimeMillis())) + "\t" + line
                                + "\n");
                        writer.flush();
                    } else {
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (process != null) {
                    process.destroy();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (writer != null) {
                    try {
                        writer.flush();
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                process = null;
                reader = null;
                writer = null;
            }
        }

        public void end() {
            isRunning = false;
        }
    }

    /**
     * 整个应用只需要调用一次即可:开始本地记录
     *
     * @param filePath 要写入的目的文件路径
     * @param iswrite    是否需要写入sdk
     */
    public void startWriteLogToSdcard(String filePath,boolean iswrite) {

        if (iswrite) {
            if (logWriter == null) {
                try {
                    /** LogUtil这个类的pid,必须在类外面得到 */
                    logWriter = new LogWriter(filePath, android.os.Process.myPid());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            logWriter.start();
        }
    }

    /**
     * 整个应用只需要调用一次即可:结束本地记录
     */
    public void endWriteLogToSdcard() {
        if (logWriter != null) {
            logWriter.end();
        }
    }

    /* ========================下面的是需要上传的数据========================== */
    private LogUploader logUploader;

    /**
     * 日志上传线程
     */
    private class LogUploader extends Thread {
        /**
         * 当前线程是否正在运行
         */
        private boolean isRunning = true;
        /**
         * 上传所需要的url
         */
        private String mStrUrl;
        /**
         * 上传所需要的其他参数
         */
        private HashMap<String, String> mAllParams;
        /**
         * 上传所需要pid
         */
        private int mPid;

        /**
         * 构造方法
         *
         * @param strUrl    上传所需要的url
         * @param allParams 需要上传的额外的参数【除了日志以外】
         * @param pid       日志所在的pid
         */
        public LogUploader(String strUrl, HashMap<String, String> allParams, int pid) {
            this.mStrUrl = strUrl;
            this.mAllParams = allParams;
            this.mPid = pid;
        }

        @Override
        public void run() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA);//日期格式化对象
            Process process = null;//进程
            BufferedReader reader = null;
            try {
                //执行命令行,得到输入流
                String cmd = "logcat *:e *:w | grep";
                process = Runtime.getRuntime().exec(cmd);
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()), 1024);
                String line = null;
                while (isRunning) {
                    line = reader.readLine();
                    if (line != null && line.length() > 0) {
                        String log = "PID:" + this.mPid + "\t"
                                + sdf.format(new Date(System.currentTimeMillis())) + "\t" + line;
                        mAllParams.put("log", log);
                    } else {
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (process != null) {
                    process.destroy();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                process = null;
                reader = null;
            }
        }

        public void end() {
            isRunning = false;
        }
    }

    /**
     * 整个应用调用一次即可：上传日志数据
     *
     * @param strUrl    上传所需要的url
     * @param allParams 需要上传的额外的参数【除了日志以外】
     * @param isUploadLog    是否需要上传
     */
    public void startUploadLog(String strUrl, HashMap<String, String>
            allParams,boolean isUploadLog) {

        if (isUploadLog) {
            if (logUploader == null) {
                logUploader = new LogUploader(strUrl, allParams, android.os.Process.myPid());
            }
            logUploader.start();
        }
    }

    /**
     * 整个应用调用一次即可：结束上传日志数据
     */
    public void endUploadLog() {
        if (logUploader != null) {
            logUploader.end();
        }
    }

}
