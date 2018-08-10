package com.fmsh.coinclient.controller;

import cn.hutool.core.codec.Base64;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fmsh.coinclient.bean.BaseData;
import com.fmsh.coinclient.bean.User;
import com.fmsh.coinclient.biz.util.Base58Check;
import com.fmsh.coinclient.biz.wallet.PairKeyPersist;
import com.fmsh.coinclient.biz.wallet.Wallet;
import com.fmsh.coinclient.biz.wallet.WalletUtils;
import com.fmsh.coinclient.common.Const;
import com.fmsh.coinclient.common.VoteAddressPersist;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Author: yuanjiaxin
 * @Date: 2018/7/13 9:55
 * @Description:
 */
@RestController
@RequestMapping("/wallet")
@Slf4j
public class WalletController {

    @Value("${manager.url}")
    private String managerUrl;

    @Value("${user.register}")
    private String userRegister;

    @Value("${wallet.generateBlock}")
    private String generateBlock;

    @Value("${wallet.checkBalance}")
    private String checkBalance;

    @Value("${wallet.getLastBlockHash}")
    private String getLastBlockHash;

    @Value("${wallet.doSend}")
    private String doSend;

    @Value("${wallet.requestCoin}")
    private String requestCoin;

    @Value("${wallet.queryRelatedBlocks}")
    private String queryRelatedBlocks;

    @Value("${wallet.queryAllBlocks}")
    private String queryAllBlocks;

    @Resource
    private RestTemplate restTemplate;

    @GetMapping("/clean")
    public void cleanWallet() {
        PairKeyPersist.setWalletMap(new HashMap<>());
    }

//    @GetMapping("/confirmUsername")
//    public String confirmUsername(String username) {
//        if (StringUtils.isBlank(username)) {
//            return "";
//        }
//        if (CollectionUtils.isEmpty(PairKeyPersist.getWalletMap()) || !PairKeyPersist.getWalletMap().get("username").equals(username)) {
//            return register(username);
//        }
//        return PairKeyPersist.getWalletMap().get("address");
//    }

    @GetMapping("/test")
    public void test() {
        // step1. create main node
        Wallet mainWallet = createNode("main");
        if (mainWallet == null) return;

        Map<String, WalletData> walletMap = new ConcurrentHashMap<>();
        WalletData mainData = walletToData(mainWallet);
        mainData.setUsername("main");
        walletMap.put("main", mainData);

        log.info("main address : {}", mainWallet.getAddress());
        requestCoinWallet(mainWallet, "main", 1000000L);

        // step2. create normal nodes and send coin from main to these nodes
        for (int i = 0; i < 1000; i++) {
            preWork(i, walletMap);
        }

        System.out.println(walletMap.get("main").getAddress());
        String mainBalance = checkUsernameBalance(walletMap, "main");
        if (Long.valueOf(mainBalance) <= 1000L) {
            return;
        }

        log.info("==========================================================");
        log.info("==========================================================");
        log.info("预注册结束");

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread th = createMainThread(i, walletMap);
            th.start();
            threads.add(th);
        }
        for (Thread th : threads) {
            try {
                th.join();
            } catch (InterruptedException ignored) {
            }
        }

        log.info("complete init, please wait 3 seconds");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
        }

        log.info("wait end");
        log.info("==========================================================");
        log.info("==========================================================");

        // step3. send coins between nodes
//        List<Thread> threadList = new ArrayList<>();
//        for (int i = 0; i < 10; i++) {
//            Thread th = createNormalThread(i, walletMap);
//            th.start();
//            threadList.add(th);
//        }
//        for (Thread th : threadList) {
//            try {
//                th.join();
//            } catch (InterruptedException ignored) {
//            }
//        }
        log.info("==================================================================");
        log.info("task end...");
    }

    private void requestCoinWallet(Wallet wallet, String username, Long amount) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("pk", Base64.encode(wallet.getPublicKey(), Charset.defaultCharset()));
        data.put("sk", Base64.encode(wallet.getPrivateKey().getEncoded(), Charset.defaultCharset()));
        data.put("amount", amount);
        String hash = restTemplate.postForEntity(VoteAddressPersist.getVoteUrl() + requestCoin, generateRequest(data), String.class).getBody();
        log.info("{} request Coin, hash: {}", username, hash);
    }

    private Wallet createNode(String username) {
        Wallet wallet = WalletUtils.getInstance().createWallet();
        String address = wallet.getAddress();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
        map.add("username", username);
        map.add("address", address);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        BaseData base = restTemplate.postForObject(managerUrl + "user/register", request, BaseData.class);
        if (base.getCode() != 0) {
            log.error("{} node register error!", username);
            return null;
        }
        return wallet;
    }

    private void preWork(Integer count, Map<String, WalletData> walletMap) {
        String username = String.valueOf(count);
        Wallet wallet = createNode(username);
        if (wallet == null) {
            log.error("{} node create fail");
            return;
        }
        WalletData data = walletToData(wallet);
        data.setUsername(username);

        walletMap.put(username, data);
        log.info("=========================================成功注册: {}", username);
    }

    private WalletData walletToData(Wallet wallet) {
        WalletData data = new WalletData();
        data.setAddress(wallet.getAddress());
        data.setPk(Base64.encode(wallet.getPublicKey(), Charset.defaultCharset()));
        data.setSk(Base64.encode(wallet.getPrivateKey().getEncoded(), Charset.defaultCharset()));
        return data;
    }

    private static class WalletData {
        private String username;
        private String address;
        private String pk;
        private String sk;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getPk() {
            return pk;
        }

        public void setPk(String pk) {
            this.pk = pk;
        }

        public String getSk() {
            return sk;
        }

        public void setSk(String sk) {
            this.sk = sk;
        }
    }

    private String checkUsernameBalance(Map<String, WalletData> walletMap, String username) {
        WalletData walletData = walletMap.get(username);
        Map<String, Object> data = new HashMap<>();
        data.put("address", walletData.getAddress());
        String balance = restTemplate.postForEntity(VoteAddressPersist.getVoteUrl() + checkBalance, generateRequest(data), String.class).getBody();
        log.info("{} balance is: {}", username, balance);
        return balance;
    }

    private Thread createMainThread(Integer count, Map<String, WalletData> walletMap) {
        return new Thread(() -> {
            for (int i = 100*count; i < 100*(count + 1); i++) {
                log.info("from: {} ===========> to: {}", "main", String.valueOf(i));
                sendFromTo("main", String.valueOf(i), 1000L, walletMap);
                sendFromTo(String.valueOf(i), "main", 1L, walletMap);
            }
        });
    }

//    private Thread createNormalThread(Integer senderCount, Map<String, WalletData> walletMap) {
//        return new Thread(() -> {
//            for (int i = 100*senderCount; i < 100*(senderCount + 1); i++) {
//                log.info("from: {} ===========> to: {}", String.valueOf(i), "main");
//                sendFromTo(String.valueOf(i), "main", 1L, walletMap);
//            }
//        });
//    }

    private void sendFromTo(String sender, String receiver, Long amount, Map<String, WalletData> walletMap) {
        WalletData senderWallet = walletMap.get(sender);
        Map<String, Object> data = new HashMap<>();
        data.put("sender", sender);
        data.put("receiver", receiver);
        data.put("pk", senderWallet.getPk());
        data.put("sk", senderWallet.getSk());
        data.put("amount", amount);

        restTemplate.postForEntity(VoteAddressPersist.getVoteUrl() + doSend, generateRequest(data), String.class).getBody();
        log.info("{} node send {} coins to {} node", sender, amount, receiver);
    }

//    @GetMapping("/init")
//    @ResponseBody
//    public String register(String username) {
//        if (StringUtils.isEmpty(username)) {
//            return "";
//        }
//        Wallet wallet = WalletUtils.getInstance().createWallet();
//        String address = wallet.getAddress();
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//
//        MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
//        map.add("username", username);
//        map.add("address", address);
//
//        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
//
//        BaseData base = restTemplate.postForObject(managerUrl + "user/register", request, BaseData.class);
//        if (base.getCode() == 0) {
//            Map<String, String> walletMap = new HashMap<>();
//            walletMap.put("username", username);
//            walletMap.put("address", address);
//            PairKeyPersist.setWalletMap(walletMap);
//            writeUserConfig();
//            return PairKeyPersist.getWalletMap().get("address");
//        }
//        return "";
//    }

    @GetMapping("/getBalance")
    @ResponseBody
    public String getBalance() {
        if (PairKeyPersist.getWalletMap().get("username") == null) {
            return "您还没有初始化钱包";
        }
        String address = PairKeyPersist.getWalletMap().get("address");

        // 检查钱包地址是否合法
        try {
            Base58Check.base58ToBytes(address);
        } catch (Exception e) {
            log.error("ERROR: invalid wallet address", e);
            throw new RuntimeException("ERROR: invalid wallet address", e);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("address", address);
        String balance = restTemplate.postForEntity(VoteAddressPersist.getVoteUrl() + checkBalance, generateRequest(data), String.class).getBody();
        return "address: " + address + "  balance: " + balance;
    }

    @GetMapping("/send")
    @ResponseBody
    public String send(String receiver, Long amount) {
        String from = PairKeyPersist.getWalletMap().get("address");
        // 检查钱包地址是否合法
        try {
            Base58Check.base58ToBytes(from);
        } catch (Exception e) {
            log.error("ERROR: sender address invalid ! address=" + from, e);
            throw new RuntimeException("ERROR: sender address invalid ! address=" + from, e);
        }

        if (amount < 1 || amount > (Long.MAX_VALUE - 1) / 2) {
            log.error("ERROR: amount invalid ! amount=" + amount);
            throw new RuntimeException("ERROR: amount invalid ! amount=" + amount);
        }

        Wallet sender = WalletUtils.getInstance().getWallet(PairKeyPersist.getWalletMap().get("address"));
        Map<String, Object> data = new HashMap<>();
        data.put("sender", PairKeyPersist.getWalletMap().get("username"));
        data.put("receiver", receiver);
        byte[] pk = sender.getPublicKey();
        String pkString = Base64.encode(pk, Charset.defaultCharset());

        data.put("pk", pkString);
        data.put("sk", Base64.encode(sender.getPrivateKey().getEncoded(), Charset.defaultCharset()));
        data.put("amount", amount);
        return restTemplate.postForEntity(VoteAddressPersist.getVoteUrl() + doSend, generateRequest(data), String.class).getBody();
    }

    @GetMapping("/requestCoin")
    @ResponseBody
    public String requestCoin(Long amount) {
        if (amount < 1 || amount > (Long.MAX_VALUE - 1) / 2) {
            log.error("ERROR: amount invalid ! amount=" + amount);
            throw new RuntimeException("ERROR: amount invalid ! amount=" + amount);
        }
        Wallet sender = WalletUtils.getInstance().getWallet(PairKeyPersist.getWalletMap().get("address"));

        Map<String, Object> data = new HashMap<>();
        data.put("username", PairKeyPersist.getWalletMap().get("username"));

        byte[] pk = sender.getPublicKey();
        String pkString = Base64.encode(pk, Charset.defaultCharset());
        data.put("pk", pkString);
        data.put("sk", Base64.encode(sender.getPrivateKey().getEncoded(), Charset.defaultCharset()));
        data.put("amount", amount);
        return restTemplate.postForEntity(VoteAddressPersist.getVoteUrl() + requestCoin, generateRequest(data), String.class).getBody();
    }

    @GetMapping("/queryHistory")
    @ResponseBody
    public String queryHistory() {
        Wallet sender = WalletUtils.getInstance().getWallet(PairKeyPersist.getWalletMap().get("address"));

        Map<String, Object> data = new HashMap<>();
        data.put("username", PairKeyPersist.getWalletMap().get("username"));

        byte[] pk = sender.getPublicKey();
        String pkString = Base64.encode(pk, Charset.defaultCharset());
        data.put("pk", pkString);

        return restTemplate.postForEntity(VoteAddressPersist.getVoteUrl() + queryRelatedBlocks, generateRequest(data), String.class).getBody();
    }

    @GetMapping("/queryAllBlocks")
    @ResponseBody
    public String queryAllBlocks() {
        return restTemplate.getForEntity(VoteAddressPersist.getVoteUrl() + queryAllBlocks, String.class).getBody();
    }

    @GetMapping("/listUsers")
    @ResponseBody
    public String listUsers() {
        List<User> users = JSONArray.parseArray(restTemplate.getForEntity(managerUrl + "user/listUsers", String.class).getBody(), User.class);
        return JSONObject.toJSONString(users.stream().map(User::getUsername).collect(Collectors.toList()));
    }

    @GetMapping("/getLeader")
    @ResponseBody
    public String getLeader() {
        return restTemplate.getForEntity(managerUrl + "member/getLeader", String.class).getBody();
    }

    @GetMapping("/getVoteUrl")
    @ResponseBody
    public String getVoteUrl() {
        return VoteAddressPersist.getVoteUrl();
    }

    private HttpEntity<String> generateRequest(Map<String, Object> data) {
        HttpHeaders headers = new HttpHeaders();
        MediaType type = MediaType.parseMediaType("application/json; charset=UTF-8");
        headers.setContentType(type);
        headers.add("Accept", MediaType.APPLICATION_JSON.toString());

        JSONObject jsonObj = new JSONObject();
        for (String key : data.keySet()) {
            jsonObj.put(key, data.get(key));
        }
        return new HttpEntity<>(jsonObj.toString(), headers);
    }

    private void writeUserConfig() {
        try {
            PrintWriter writer = new PrintWriter(Const.USER_PROPERTIES, "UTF-8");
            writer.println("username=" + PairKeyPersist.getWalletMap().get("username"));
            writer.println("address=" + PairKeyPersist.getWalletMap().get("address"));
            writer.close();
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            log.error("创建本地用户信息文件失败");
        }

    }
}
