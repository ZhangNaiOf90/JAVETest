package com.jave.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.sun.org.apache.bcel.internal.generic.NEW;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class Applaction2 {
    // ffmpeg.exe全路径
    private static final String FFMPEG_PATH = "D:/MyTools/ffmpeg/bin/ffmpeg.exe";
    private static ThreadPoolExecutor threadPoolExecutor= new ThreadPoolExecutor(
            5,
            200,
            6L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(15),
            Executors.defaultThreadFactory(),
            new  ThreadPoolExecutor.CallerRunsPolicy()
            );
    public static void main(String[] args) throws Exception {
//        H:\videos\137\59
        File rootDir = new File("H:"+File.separator+"videos"+File.separator+"137"+File.separator+"59");
        String outputDir = "H:"+File.separator+"videos"+ File.separator + "out";
        // 开始处理文件
        renameAndCopyFile(rootDir, outputDir);
    }

    /**
     * 具体合成视频函数
     *
     * @param videoInputPath    原视频的全路径
     *
     * @param audioInputPath    音频的全路径
     *
     * @param videoOutPath      视频与音频结合之后的视频的路径
     */
    public static void convetor(String videoInputPath, String audioInputPath, String videoOutPath) throws Exception {
        Process process = null;
        InputStream errorStream = null;
        InputStreamReader inputStreamReader = null;
        BufferedReader br = null;
        try {
            // ffmpeg命令
            String command = FFMPEG_PATH + " -i " + videoInputPath + " -i " + audioInputPath
                    + " -c:v copy -c:a aac -strict experimental " + " -map 0:v:0 -map 1:a:0 " + " -y " + videoOutPath;
            process = Runtime.getRuntime().exec(command);
            errorStream = process.getErrorStream();
            inputStreamReader = new InputStreamReader(errorStream);
            br = new BufferedReader(inputStreamReader);
            // 用来收集错误信息的
            String str = "";
            while ((str = br.readLine()) != null) {
                System.out.println(str);
            }
            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                br.close();
            }
            if (inputStreamReader != null) {
                inputStreamReader.close();
            }
            if (errorStream != null) {
                errorStream.close();
            }
        }
    }

    // 遍历每个路径，找到entry.json文件所在路径
    public static void renameAndCopyFile(File rootDir, String outputDir) throws Exception {
        File dir = rootDir;
        if (dir == null) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        String fileName = null;
        for (File f : files) {
            if (f.isFile()) {
                fileName = f.getName();
                if ("entry.json".equalsIgnoreCase(fileName)) {
                    String jsonStr = FileUtil.readString(f, "UTF-8");
                    if (jsonStr == null || jsonStr.isEmpty()) {
                        return;
                    }
                    Entry entry = getEntry(jsonStr);
                    // 文件名
                    String realFileName = entry.page_data.part;
                    realFileName = realFileName.replaceAll("\\s+", "-");
                    // 开始迭代查找视频和音频文件，并将数据拷贝到其他地方
                    List<FileInfo> fileList = findVideoAudioFile(dir);
                    // 对文件进行合并
                    String videoInputPath = null;
                    String audioInputPath = null;
                    String videoOutPath = outputDir + File.separator + realFileName + ".mp4";
                    System.out.println(videoOutPath);
                    for (FileInfo info : fileList) {
                        if ("audio.m4s".equalsIgnoreCase(info.fileName)) {
                            audioInputPath = info.file.getAbsolutePath();
                        }
                        if ("video.m4s".equalsIgnoreCase(info.fileName)) {
                            videoInputPath = info.file.getAbsolutePath();
                        }
                    }
                    String finalVideoInputPath = videoInputPath;
                    String finalAudioInputPath = audioInputPath;
                    threadPoolExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                convetor(finalVideoInputPath, finalAudioInputPath, videoOutPath);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    convetor(videoInputPath, audioInputPath, videoOutPath);
                }
            } else if (f.isDirectory()) {
                renameAndCopyFile(f, outputDir);
            }
        }
    }

    private static List<FileInfo> findVideoAudioFile(File dir) {
        if (dir == null) {
            return new ArrayList<FileInfo>();
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return new ArrayList<FileInfo>();
        }
        List<FileInfo> result = new ArrayList<FileInfo>();
        String fileName = null;
        for (File f : files) {
            if (f.isFile()) {
                fileName = f.getName();
                if (fileName.endsWith("m4s")) {
                    result.add(new FileInfo(f));
                }
            } else if (f.isDirectory()) {
                result.addAll(findVideoAudioFile(f));
            }
        }
        return result;
    }

    public static class FileInfo {
        public File file;
        public String fileName;

        public FileInfo(File file) {
            this.file = file;
            if (file != null) {
                fileName = file.getName();
            }
        }
    }

    // 解析bilibili下的entry.json文件
    public static Entry getEntry(String jsonText) {
        return JSONUtil.toBean(jsonText, Entry.class);
    }

    public static class PageData {
        // 文件名
        public String part;
    }

    public static class Entry {
        // 是否已经下载完成
        public boolean is_completed;
        // 课程名/专辑名
        public String title;
        // 文件信息
        public PageData page_data;
    }
}