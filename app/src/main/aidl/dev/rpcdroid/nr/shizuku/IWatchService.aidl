package dev.rpcdroid.nr.shizuku;

import dev.rpcdroid.nr.shizuku.IWatchCallback;

interface IWatchService {
    void destroy() = 16777114;   // id baku yang dipakai Shizuku untuk mematikan UserService
    void start(IWatchCallback cb) = 1;
    void stop() = 2;
}
