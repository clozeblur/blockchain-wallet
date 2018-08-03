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

    @GetMapping("/confirmUsername")
    public String confirmUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return "";
        }
        if (CollectionUtils.isEmpty(PairKeyPersist.getWalletMap()) || !PairKeyPersist.getWalletMap().get("username").equals(username)) {
            return register(username);
        }
        return PairKeyPersist.getWalletMap().get("address");
    }

    @GetMapping("/init")
    @ResponseBody
    public String register(String username) {
        if (StringUtils.isEmpty(username)) {
            return "";
        }
        Wallet wallet = WalletUtils.getInstance().createWallet();
        String address = wallet.getAddress();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
        map.add("username", username);
        map.add("address", address);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        BaseData base = restTemplate.postForObject(managerUrl + "user/register", request, BaseData.class);
        if (base.getCode() == 0) {
            Map<String, String> walletMap = new HashMap<>();
            walletMap.put("username", username);
            walletMap.put("address", address);
            PairKeyPersist.setWalletMap(walletMap);
            writeUserConfig();
            return PairKeyPersist.getWalletMap().get("address");
        }
        return "";
    }

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
    public String send(String receiver, Integer amount) {
        String from = PairKeyPersist.getWalletMap().get("address");
        // 检查钱包地址是否合法
        try {
            Base58Check.base58ToBytes(from);
        } catch (Exception e) {
            log.error("ERROR: sender address invalid ! address=" + from, e);
            throw new RuntimeException("ERROR: sender address invalid ! address=" + from, e);
        }

        if (amount < 1) {
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
    public String requestCoin(Integer amount) {
        if (amount < 1) {
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
