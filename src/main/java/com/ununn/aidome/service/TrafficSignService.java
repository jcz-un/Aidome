package com.ununn.aidome.service;

import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface TrafficSignService {

    // 识别交通标志
    String analyzeTrafficSign(MultipartFile image) throws IOException, NoApiKeyException, InputRequiredException, UploadFileException;
}
