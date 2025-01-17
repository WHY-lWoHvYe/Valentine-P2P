/*
 *    Copyright (c) 2022-2025.  lWoHvYe(Hongyan Wang)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.lwohvye.log.service.local;

import com.lwohvye.log.domain.BzLog;
import com.lwohvye.log.service.IBzLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MultiLogService {
    //    -------------------记录鉴权信息-----------------------------
    @Autowired
    private IBzLogService bzLogService;

    public void saveAuthorizeLog(String msgData) {
        var opLog = new BzLog().setDescription("记录用户登录信息").setLogType("Auth").setParams(msgData);
        bzLogService.save(opLog);
    }

    public void saveMultiLog(String msgType, String msgData, String desc) {
        bzLogService.save(new BzLog().setLogType(msgType).setParams(msgData).setDescription(desc));
    }
}
