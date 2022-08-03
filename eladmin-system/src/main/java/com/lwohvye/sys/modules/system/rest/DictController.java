/*
 *  Copyright 2019-2020 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.lwohvye.sys.modules.system.rest;

import com.lwohvye.annotation.log.Log;
import com.lwohvye.api.modules.system.service.dto.DictDto;
import com.lwohvye.base.BaseEntity.Update;
import com.lwohvye.exception.BadRequestException;
import com.lwohvye.api.modules.system.domain.Dict;
import com.lwohvye.sys.modules.system.service.IDictService;
import com.lwohvye.api.modules.system.service.dto.DictQueryCriteria;
import com.lwohvye.utils.result.ResultInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * @author Zheng Jie
 * @date 2019-04-10
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "DictController", description = "系统：字典管理")
@RequestMapping("/api/sys/dict")
public class DictController {

    private final IDictService dictService;
    private static final String ENTITY_NAME = "dict";

    @Operation(summary = "导出字典数据")
    @GetMapping(value = "/download")
    public void download(HttpServletResponse response, DictQueryCriteria criteria) throws IOException {
        dictService.download(dictService.queryAll(criteria), response);
    }

    @Operation(summary = "查询字典")
    @GetMapping(value = "/all")
    public ResponseEntity<ResultInfo<DictDto>> queryAll() {
        return new ResponseEntity<>(ResultInfo.success(dictService.queryAll(new DictQueryCriteria())), HttpStatus.OK);
    }

    @Operation(summary = "查询字典")
    @GetMapping
    public ResponseEntity<ResultInfo<Map<String, Object>>> query(DictQueryCriteria resources, Pageable pageable) {
        return new ResponseEntity<>(ResultInfo.success(dictService.queryAll(resources, pageable)), HttpStatus.OK);
    }

    @Log("新增字典")
    @Operation(summary = "新增字典")
    @PostMapping
    public ResponseEntity<ResultInfo<String>> create(@Validated @RequestBody Dict resources) {
        if (resources.getId() != null) {
            throw new BadRequestException("A new " + ENTITY_NAME + " cannot already have an ID");
        }
        dictService.create(resources);
        return new ResponseEntity<>(ResultInfo.success(), HttpStatus.CREATED);
    }

    @Log("修改字典")
    @Operation(summary = "修改字典")
    @PutMapping
    public ResponseEntity<ResultInfo<String>> update(@Validated(Update.class) @RequestBody Dict resources) {
        dictService.update(resources);
        return new ResponseEntity<>(ResultInfo.success(), HttpStatus.NO_CONTENT);
    }

    @Log("删除字典")
    @Operation(summary = "删除字典")
    @DeleteMapping
    public ResponseEntity<ResultInfo<String>> delete(@RequestBody Set<Long> ids) {
        dictService.delete(ids);
        return new ResponseEntity<>(ResultInfo.success(), HttpStatus.OK);
    }
}
