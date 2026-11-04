package dev.rpcdroid.nr.shizuku;

/** Dipanggil dari proses shell ke app. oneway: tidak pernah memblokir pembaca log. */
oneway interface IWatchCallback {
    void onResumed(String pkg) = 1;
    void onProcDied(String proc) = 2;
}
