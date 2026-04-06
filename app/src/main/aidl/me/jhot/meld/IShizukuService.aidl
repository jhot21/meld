package me.jhot.meld;

interface IShizukuService {
    void grantSecureSettings(String packageName);
    void putSetting(String namespace, String key, int value);
    void setBluetooth(boolean enable);
    void setMediaVolumeDirect(int volume);
    void destroy();
}
