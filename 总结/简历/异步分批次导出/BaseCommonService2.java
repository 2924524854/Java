package com.oppo.corehrpt.basic.service;

import com.oppo.corehrpt.basic.export.entity.ComExportRecordVO;
import com.oppo.corehrpt.basic.pojo.vo.GBassFilePO;
import com.oppo.corehrpt.basic.service.rpc.BaseCommonRpcApi;
import com.oppo.gcommon.starter.base.context.UserContextHolder;
import com.oppo.gcommon.starter.web.vo.ResultVo;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @date 2025-02-09
 * @author Wenjie.Xiang
 */
@Slf4j
@Service
public class BaseCommonService2 {

    @Autowired
    private BaseCommonRpcApi baseCommonRpcApi;

    /**
     * 创建导出记录
     *
     * @param emplid 用户工号
     * @param taskType 任务类型
     * @param taskDesc 任务描述
     * @param filename 文件名
     * @return 导出记录ID
     */
    public ResultVo<Long> createExportRecord(String emplid, String taskType, String taskDesc, String filename) {
        ComExportRecordPO po = new ComExportRecordPO();
        po.setEmplid(emplid);
        po.setFileName(filename);
        po.setTaskType(taskType);
        po.setTaskDesc(taskDesc);
        po.setStatus(0);
        po.setStartTime(new Date());
        po.setCreateDate(po.getStartTime());
        po.setCreator(UserContextHolder.getUserAccount());
        po.setCreatorName(UserContextHolder.getUserName());
        po.setUpdateDate(po.getCreateDate());
        po.setUpdateBy(po.getCreator());
        po.setUpdateByName(po.getCreatorName());
        po.setType(type);
        baseMapper.insert(po);
        return po.getId();
    }

    /**
     * 更新导出记录
     *
     * @param recordVO 导出记录VO
     * @return 更新结果
     */
    public ResultVo<Long> updateExportRecord(ComExportRecordVO recordVO) {
        exportRecordPO.setUpdateDate(new Date());
        exportRecordPO.setUpdateBy(UserContextHolder.getUserAccount());
        exportRecordPO.setUpdateByName(UserContextHolder.getUserName());
        baseMapper.updateById(exportRecordPO);
    }

    /**
     * 上传文件
     *
     * @param file 文件
     * @param securiedType 安全类型
     * @param operator 操作人
     * @param users 用户列表
     * @param expireTime 过期时间
     * @return 文件信息
     */
    public ResultVo<GBassFilePO> uploadFile(MultipartFile file, Integer securiedType, String operator, String users, Long expireTime) {
        Long fileSize = file.getSize();
        logger.info("receive file:{} length:{}", file.getOriginalFilename(), fileSize);
        GBassFilePO gBassFilePO = fileService.uploadFile(file.getOriginalFilename(), securiedType,
                file.getInputStream(), operator, users, expireTime, tag);

        return gBassFilePO;
    }
}
