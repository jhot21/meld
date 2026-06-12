package me.jhot.meld;

import me.jhot.meld.service.SettingChange;

interface IShizukuService {
    void grantSecureSettings(String packageName);
    void putSettings(in List<SettingChange> settings);
    void setBluetooth(boolean enable);
    void setMediaVolumeDirect(int volume);
    void destroy();
}
