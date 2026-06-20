package com.vyllo.music;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import java.util.Map;
import java.util.HashMap;
import java.io.InputStream;
import java.net.URL;
import java.net.HttpURLConnection;

public class TestComments {
    public static void main(String[] args) throws Exception {
        NewPipe.init(DownloaderImpl.getInstance());
        System.out.println("Fetching...");
        try {
            CommentsInfo info = CommentsInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
            System.out.println("Name: " + info.getName());
            System.out.println("Items: " + info.getRelatedItems().size());
            if(info.getRelatedItems().size() > 0) {
                System.out.println("First comment: " + info.getRelatedItems().get(0).getCommentText().getContent());
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}

class DownloaderImpl extends Downloader {
    private static DownloaderImpl instance;
    public static DownloaderImpl getInstance() {
        if (instance == null) instance = new DownloaderImpl();
        return instance;
    }
    private DownloaderImpl() { }
    @Override
    public Response execute(Request request) throws java.io.IOException, org.schabi.newpipe.extractor.exceptions.ReCaptchaException {
        String httpMethod = request.httpMethod();
        String urlStr = request.url();
        Map<String, java.util.List<String>> headers = request.headers();
        byte[] dataToSend = request.dataToSend();
        
        HttpURLConnection connection = (HttpURLConnection) new URL(urlStr).openConnection();
        connection.setRequestMethod(httpMethod);
        
        if (headers != null) {
            for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
                for (String value : entry.getValue()) {
                    connection.addRequestProperty(entry.getKey(), value);
                }
            }
        }
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36");
        
        if (dataToSend != null) {
            connection.setDoOutput(true);
            connection.getOutputStream().write(dataToSend);
        }
        
        int responseCode = connection.getResponseCode();
        String responseMessage = connection.getResponseMessage();
        Map<String, java.util.List<String>> responseHeaders = connection.getHeaderFields();
        
        InputStream in;
        if (responseCode >= 400) {
            in = connection.getErrorStream();
        } else {
            in = connection.getInputStream();
        }
        
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        if (in != null) {
            while ((nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        }
        String responseBody = new String(buffer.toByteArray(), "UTF-8");
        return new Response(responseCode, responseMessage, responseHeaders, responseBody, request.url());
    }
}
