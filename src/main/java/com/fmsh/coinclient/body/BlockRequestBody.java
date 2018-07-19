package com.fmsh.coinclient.body;

import com.fmsh.coinclient.biz.block.BlockBody;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @Author: yuanjiaxin
 * @Date: 2018/7/5 10:21
 * @Description: 生成Block时传参
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class BlockRequestBody {

    private String publicKey;
    private BlockBody blockBody;
}
