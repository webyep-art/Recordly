package com.recordly.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class RecordlyMetadata {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String fileFormat = "MCPR";
    private int fileFormatVersion = 14;
    private int protocolVersion = 767;
    private String mcVersion = "1.21.1";
    private String generator = "Recordly";
    private long date = System.currentTimeMillis();
    private int duration = 0;
    private String serverName = "Singleplayer";
    private boolean singleplayer = true;

    public RecordlyMetadata() {
    }

    public static RecordlyMetadata fromJson(InputStream inputStream) {
        return GSON.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), RecordlyMetadata.class);
    }

    public void writeTo(OutputStream outputStream) throws Exception {
        String json = GSON.toJson(this);
        outputStream.write(json.getBytes(StandardCharsets.UTF_8));
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
    }

    public int getFileFormatVersion() {
        return fileFormatVersion;
    }

    public void setFileFormatVersion(int fileFormatVersion) {
        this.fileFormatVersion = fileFormatVersion;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getMcVersion() {
        return mcVersion;
    }

    public void setMcVersion(String mcVersion) {
        this.mcVersion = mcVersion;
    }

    public String getGenerator() {
        return generator;
    }

    public void setGenerator(String generator) {
        this.generator = generator;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public boolean isSingleplayer() {
        return singleplayer;
    }

    public void setSingleplayer(boolean singleplayer) {
        this.singleplayer = singleplayer;
    }
}
