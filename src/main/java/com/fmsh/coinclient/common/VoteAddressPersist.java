package com.fmsh.coinclient.common;

/**
 * @Author: yuanjiaxin
 * @Date: 2018/7/13 10:17
 * @Description:
 */
public class VoteAddressPersist {

    private VoteAddressPersist() {}

    private static volatile String ip;

    private static volatile Integer port;

    public static void setIp(String ip) {
        VoteAddressPersist.ip = ip;
    }

    public static void setPort(Integer port) {
        VoteAddressPersist.port = port;
    }

    public static String getVoteUrl() {
        return Const.SCHEME_HEAD + ip + Const.SEPARATOR + port;
    }
}
