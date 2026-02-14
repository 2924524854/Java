package com.oppo.corehrpt.basic.export.helper;


import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.row.SimpleRowHeightStyleStrategy;
import com.oppo.corehrpt.basic.enums.LanguageEnum;
import com.oppo.corehrpt.basic.export.annotation.ExcelColumn;
import com.oppo.corehrpt.basic.export.annotation.ExcelModel;
import com.oppo.corehrpt.basic.export.entity.ComExportRecordVO;
import com.oppo.corehrpt.basic.export.entity.ExcelModelEntity;
import com.oppo.corehrpt.basic.export.strategy.ExcelCellStyleStrategy;
import com.oppo.corehrpt.basic.export.strategy.WidthStyleStrategy;
import com.oppo.corehrpt.basic.pojo.query.page.PageQuery;
import com.oppo.corehrpt.basic.pojo.vo.GBassFilePO;
import com.oppo.corehrpt.basic.service.BaseCommonService2;
import com.oppo.corehrpt.basic.util.AsyncRequestTokenHelper;
import com.oppo.corehrpt.basic.util.DateUtil;
import com.oppo.gcommon.starter.base.bean.LoginUserBean;
import com.oppo.gcommon.starter.base.context.UserContextHolder;
import com.oppo.gcommon.starter.base.exception.SimpleException;
import com.oppo.gcommon.starter.mybatis.pojo.vo.PageResultVO;
import com.oppo.gcommon.starter.web.vo.ResultVo;
import jodd.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Excel导出助手工具类，提供异步Excel导出功能，支持百万级数据导出
 */
@Slf4j
@Component
public class ExportHelper2 {

    /** 基础公共服务，用于文件上传和导出记录管理 */
    private static BaseCommonService2 baseCommonService2;

    /** 线程池执行器，用于异步任务执行 */
    private static ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /** 临时文件存储路径 */
    private final static String TEMP_DIR_PATH = "/home/corehr/DOCs/tmp";

    /** 默认分页查询条数 */
    private final static int DEFAULT_PAGE_SIZE = 500;

    /** Excel单个Sheet页最大行数限制 */
    private final static int SHEET_MAX = 1000000;

    /**
     * 异步导出Excel（基础版本）
     *
     * @param clazz   数据模型类，必须包含@ExcelModel和@ExcelColumn注解
     * @param handler 分页查询处理器，用于获取数据
     * @param query   分页查询条件，包含分页参数
     */
    public static <T> void asyncExportExcel(Class<T> clazz, ExportHelper.ExportPageHandler<T> handler, PageQuery query) {
        asyncExportExcel(clazz, handler, query, null, null);
    }

    /**
     * 异步导出Excel（字段过滤版本）
     *
     * @param clazz            数据模型类，必须包含@ExcelModel和@ExcelColumn注解
     * @param handler          分页查询处理器，用于获取数据
     * @param query            分页查询条件，包含分页参数
     * @param excludeFieldList 要排除的字段名列表
     */
    public static <T> void asyncExportExcel(Class<T> clazz, ExportHelper.ExportPageHandler<T> handler, PageQuery query, List<String> excludeFieldList) {
        asyncExportExcel(clazz, handler, query, excludeFieldList, null);
    }

    /**
     * 异步导出Excel（完整版本）
     *
     * @param clazz            数据模型类，必须包含@ExcelModel和@ExcelColumn注解
     * @param handler          分页查询处理器，用于获取主要数据
     * @param query            分页查询条件，包含分页参数
     * @param excludeFieldList 要排除的字段名列表，可为null
     * @param secondSheetList  第二个Sheet页的数据，可为null
     */
    public static <T> void asyncExportExcel(Class<T> clazz, ExportHelper.ExportPageHandler<T> handler, PageQuery query, List<String> excludeFieldList, List<?> secondSheetList) {
        // 获取Excel模型信息
        ExcelModelEntity entity = extractExcelModel(clazz, excludeFieldList);
        if (StringUtils.isBlank(entity.getTaskType())) {
            throw new SimpleException("The task type cannot be empty.");
        }

        entity.setSecondSheetList(secondSheetList);
        LoginUserBean loginUserBean = UserContextHolder.get();
        Locale locale = LocaleContextHolder.getLocale();

        // 导出中心创建任务
        ResultVo<Long> rv = baseCommonService2.createExportRecord(loginUserBean.getUserName(), entity.getTaskType(), entity.getFileName(), entity.getFileName());
        long exportRecordId = rv.getData();

        // 异步导出
        CompletableFuture.runAsync(() -> {
            UserContextHolder.setUserInfo(loginUserBean);
            LocaleContextHolder.setLocale(locale);
            AsyncRequestTokenHelper.setLang(locale.toLanguageTag());
            // 执行异步导出
            executeAsyncExport(handler, query, entity, exportRecordId);
        }, threadPoolTaskExecutor);
    }

    /**
     * 执行异步导出核心逻辑
     *
     * @param handler 分页查询处理器，用于获取数据
     * @param query   分页查询条件，包含分页参数
     * @param entity  导出参数实体，包含Excel配置信息
     * @param exportRecordId 导出记录ID，用于跟踪导出任务状态
     */
    private static <T> void executeAsyncExport(ExportHelper.ExportPageHandler<T> handler, PageQuery query, ExcelModelEntity entity, long exportRecordId) {
        String fileId = null;
        String errorMsg = "success";
        try {
            // 导出并上传文件
            fileId = exportAndUploadExcel(entity, handler, query);
            if (StringUtils.isBlank(fileId)) {
                errorMsg = "Failed";
            }
        } catch (Exception e) {
            log.error("Export excel error", e);
            errorMsg = e.getMessage();
            if (errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
        } finally {
            // 更新导出记录
            updateExportRecord(entity, exportRecordId, fileId, errorMsg);
        }
    }

    /**
     * 更新导出记录状态
     *
     * @param entity 导出参数实体，包含文件名等信息
     * @param exportRecordId 导出记录ID
     * @param fileId 上传成功后的文件ID，null表示失败
     * @param errorMsg 错误信息，成功时为"success"
     */
    private static void updateExportRecord(ExcelModelEntity entity, long exportRecordId, String fileId, String errorMsg) {
        ComExportRecordVO recordVO = new ComExportRecordVO();
        recordVO.setEndTime(new Date());
        recordVO.setStatus(fileId != null ? 1 : 2);
        recordVO.setFileId(fileId);
        recordVO.setFileName(entity.getFileName());
        recordVO.setId(exportRecordId);
        recordVO.setErrorMsg(errorMsg);
        baseCommonService2.updateExportRecord(recordVO);
    }

    /**
     * 导出Excel文件并上传到文件服务器
     *
     * @param entity 导出参数实体，包含Excel配置信息
     * @param handler 分页查询处理器，用于获取数据
     * @param query 分页查询条件
     * @return 上传成功后的文件ID
     */
    private static <T> String exportAndUploadExcel(ExcelModelEntity entity, ExportHelper.ExportPageHandler<T> handler, PageQuery query) {
        String userId = UserContextHolder.getUserAccount();

        // 导出到临时文件
        File tempFile = getTempFile(entity.getTaskType(), userId);
        ExcelWriter writer = getExcelWriter(tempFile);
        queryAndWrite(writer, entity, handler, query);
        writer.finish();

        // 文件上传到文件服务器
        return uploadFile(userId, tempFile);
    }

    /**
     * 上传文件到文件服务器
     *
     * @param userId 用户ID，用于文件管理
     * @param tempFile 要上传的临时文件
     * @return 上传成功后的文件ID
     */
    private static String uploadFile(String userId, File tempFile) {
        MultipartFile multipartFile = com.oppo.corehrpt.basic.util.FileUtil.fileToMultipartFile(tempFile);
        ResultVo<GBassFilePO> resultVo = baseCommonService2.uploadFile(multipartFile, 1, userId, "", null);
        return resultVo.getData().getGbassFileId();
    }


    /**
     * 分页查询数据并写入Excel
     *
     * @param writer  ExcelWriter，用于写入Excel文件
     * @param entity  导出参数实体，包含Excel配置信息
     * @param handler 分页查询处理器，用于获取分页数据
     * @param query   分页查询条件，包含分页参数
     */
    private static <T> void queryAndWrite(ExcelWriter writer, ExcelModelEntity entity, ExportHelper.ExportPageHandler<T> handler, PageQuery query) {
        // 每一个工作簿可写入的数据量，需要把表头所占的行算上
        long sheetTotal = 1;
        // 工作簿编号
        int sheetNum = 1;
        // 页码
        int pageIndex = 1;
        query.setPageIndex(pageIndex);
        // 分页条数
        query.setPageSize(query.getExportPageSize() == null ? DEFAULT_PAGE_SIZE : query.getExportPageSize());
        // 不查询 Count
        query.setSearchCount(false);

        WriteSheet writeSheet = EasyExcel.writerSheet(entity.getSheetName())
                .registerWriteHandler(new ExcelCellStyleStrategy(entity.getAlignmentList()))
                .registerWriteHandler(new WidthStyleStrategy(entity.getWidthList()))
                .registerWriteHandler(new SimpleRowHeightStyleStrategy((short) entity.getTitleHeight(), (short) entity.getContentHeight()))
                .head(entity.getHeadList()).build();

        do {
            PageResultVO<T> pageResult = handler.page();
            List<T> list = pageResult.getRows();
            writer.write(getDataList(list, entity.getExcludeFieldList()), writeSheet);
            if (list.isEmpty()) {
                break;
            }

            // 条数大于Excel Sheet最大行数时，增加新的Sheet
            sheetTotal += list.size();
            if (sheetTotal >= SHEET_MAX) {
                sheetNum++;
                writeSheet = EasyExcel.writerSheet(entity.getSheetName() + "-" + sheetNum).head(entity.getHeadList()).build();
                sheetTotal = 1;
            }

            if (list.size() < query.getPageSize()) {
                // 查询结果数据量小于分页数量，处理结束
                break;
            }

            // 页码 + 1
            query.setPageIndex(++pageIndex);
        } while (true);

        if (CollectionUtils.isNotEmpty(entity.getSecondSheetList())) {
            Class<?> otherClazz = entity.getSecondSheetList().get(0).getClass();
            ExcelModelEntity otherEntity = extractExcelModel(otherClazz);
            WriteSheet writeSheet2 = EasyExcel.writerSheet(otherEntity.getSheetName())
                    .registerWriteHandler(new ExcelCellStyleStrategy(otherEntity.getAlignmentList()))
                    .registerWriteHandler(new WidthStyleStrategy(otherEntity.getWidthList()))
                    .registerWriteHandler(new SimpleRowHeightStyleStrategy((short) otherEntity.getTitleHeight(), (short) otherEntity.getContentHeight()))
                    .head(otherEntity.getHeadList()).build();
            writer.write(getDataList(entity.getSecondSheetList()), writeSheet2);
        }
    }

    /**
     * 将对象列表转换为Excel数据格式
     *
     * @param list 对象列表
     * @return Excel数据格式的二维列表
     */
    private static <T> List<List<Object>> getDataList(List<T> list) {
        return getDataList(list, null);
    }

    /**
     * 将对象列表转换为Excel数据格式
     *
     * @param list 对象列表，要转换的数据
     * @param excludeFieldList 要排除的字段名列表
     * @return Excel数据格式的二维列表，外层List代表行，内层List代表列
     */
    private static <T> List<List<Object>> getDataList(List<T> list, List<String> excludeFieldList) {
        List<List<Object>> dataList = new ArrayList<>();
        if (CollectionUtils.isEmpty(list)) {
            return dataList;
        }
        Class<?> clazz = list.get(0).getClass();
        List<Field> fieldList = extractSortFields(clazz.getDeclaredFields(), excludeFieldList, null);
        list.forEach(t -> {
            ExcelColumn excelColumn;
            List<Object> dList = new ArrayList<>();
            for (Field field : fieldList) {
                excelColumn = field.getAnnotation(ExcelColumn.class);
                try {
                    field.setAccessible(true);
                    Object o = field.get(t);
                    dList.add(getFieldValue(field, excelColumn, o));
                } catch (IllegalAccessException e) {
                    dList.add("");
                }
            }
            dataList.add(dList);
        });
        return dataList;
    }

    /**
     * 处理字段属性值，根据字段类型进行格式化转换
     *
     * @param field       字段反射对象
     * @param excelColumn Excel列注解，包含格式化信息
     * @param o           原始字段值
     * @return 转换后的字段值，适合Excel显示
     */
    private static Object getFieldValue(Field field, ExcelColumn excelColumn, Object o) {
        if (o == null) {
            return "";
        }
        Object fieldValue;
        String vType = field.getGenericType().getTypeName();
        switch (vType) {
            case "java.lang.String":
                fieldValue = o;
                if ("empNo".equalsIgnoreCase(field.getName()) || "emplid".equalsIgnoreCase(field.getName())) {
                    // 工号纯数字时，转成数值类型（防止导出的单元格带警告）
                    String v = (String) o;
                    if (StringUtils.isNumeric(v) && !v.startsWith("0")) {
                        try {
                            fieldValue = new BigDecimal(v);
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                }
                break;
            case "java.util.Date":
                String pattern = excelColumn.dateTimeFormat();
                if (StringUtils.isBlank(pattern)) {
                    pattern = DateUtil.PATTERN_STANDARD10H;
                }
                fieldValue = DateUtil.date2String((Date) o, pattern);
                break;
            default:
                fieldValue = o;
                break;
        }

        return fieldValue;
    }

    /**
     * 创建Excel写入器
     *
     * @param file 要写入的Excel文件
     * @return 配置好的ExcelWriter对象
     */
    private static ExcelWriter getExcelWriter(File file) {
        return new ExcelWriterBuilder()
                .autoCloseStream(true)
                .automaticMergeHead(false)
                .excelType(ExcelTypeEnum.XLSX)
                .file(file)
                .build();
    }

    /**
     * 创建临时Excel文件
     *
     * @param taskType 任务类型，用于文件命名
     * @param userId 用户ID，用于文件命名和隔离
     * @return 创建的临时文件对象
     * @throws SimpleException 当创建目录或文件失败时抛出
     */
    private static File getTempFile(String taskType, String userId) {
        File tempDir = new File(TEMP_DIR_PATH);
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new SimpleException("Create template dir failed.");
        }
        try {
            return FileUtil.createTempFile(taskType + "_" + userId + "_", ExcelTypeEnum.XLSX.getValue(), tempDir);
        } catch (IOException e) {
            throw new SimpleException("Create template file failed.", e);
        }
    }

    /**
     * 提取ExcelModel信息
     *
     * @param clazz Excel模板模型类
     * @return ExcelModelEntity 包含Excel配置信息的实体
     */
    private static <T> ExcelModelEntity extractExcelModel(Class<T> clazz) {
        return extractExcelModel(clazz, false);
    }

    /**
     * 提取ExcelModel信息
     *
     * @param clazz     Excel模板模型类
     * @param isExample 是否包含样例数据和备注
     * @return ExcelModelEntity 包含Excel配置信息的实体
     */
    private static <T> ExcelModelEntity extractExcelModel(Class<T> clazz, boolean isExample) {
        return extractExcelModel(clazz, null, null, isExample);
    }

    /**
     * 提取ExcelModel信息
     *
     * @param clazz            Excel模板模型类
     * @param excludeFieldList 要排除的字段名列表
     * @return ExcelModelEntity 包含Excel配置信息的实体
     */
    private static <T> ExcelModelEntity extractExcelModel(Class<T> clazz, List<String> excludeFieldList) {
        return extractExcelModel(clazz, excludeFieldList, null, false);
    }

    /**
     * 提取ExcelModel信息
     *
     * @param clazz            Excel模板模型类，必须有@ExcelModel注解
     * @param excludeFieldList 要排除的字段名列表，与includeFieldList互斥
     * @param includeFieldList 要包含的字段名列表，与excludeFieldList互斥
     * @param isExample        是否包含样例数据和备注信息
     * @return ExcelModelEntity 包含完整Excel配置信息的实体对象
     */
    private static <T> ExcelModelEntity extractExcelModel(Class<T> clazz, List<String> excludeFieldList, List<String> includeFieldList, boolean isExample) {
        // 获取 @ExcelColumn 标记字段，并按照index升序
        List<Field> fieldList = extractSortFields(clazz.getDeclaredFields(), excludeFieldList, includeFieldList);

        // ExcelColumn没有定义宽度时，取ExcelModel的宽度
        ExcelModel excelModel = clazz.getAnnotation(ExcelModel.class);
        int modelWidth = excelModel.width();
        // 标题高度
        int titleHeight = excelModel.height();
        // 文件名称
        String fileName = getFileName(excelModel);
        // Sheet 名称
        String sheetName = getSheetName(excelModel);
        // 导出任务类型
        String taskType = excelModel.taskType();

        ExcelColumn excelColumn;
        List<List<String>> headList = new ArrayList<>();
        List<Integer> widthList = new ArrayList<>();
        List<HorizontalAlignment> alignmentList = new ArrayList<>();
        List<Object> exampleList = new ArrayList<>();
        int contentHeight = 0;
        boolean first = true;
        for (Field field : fieldList) {
            excelColumn = field.getAnnotation(ExcelColumn.class);
            // 内容高度，只取第一列的值
            if (first) {
                contentHeight = excelColumn.height();
                first = false;
            }
            // 工号未指定位置时、默认居左
            if (("empNo".equalsIgnoreCase(field.getName()) || "emplid".equalsIgnoreCase(field.getName())) && excelColumn.align() == HorizontalAlignment.GENERAL) {
                alignmentList.add(HorizontalAlignment.LEFT);
            } else {
                alignmentList.add(excelColumn.align());
            }
            headList.add(Collections.singletonList(LanguageEnum.isCn() ? excelColumn.title() : (StringUtils.isBlank(excelColumn.titleEn()) ? excelColumn.title() : excelColumn.titleEn())));
            widthList.add(excelColumn.width() == -1 ? modelWidth : excelColumn.width());
            if (isExample) {
                exampleList.add(LanguageEnum.isCn() ? excelColumn.example() : (StringUtils.isBlank(excelColumn.exampleEn()) ? excelColumn.example() : excelColumn.exampleEn()));
            }
        }
        ExcelModelEntity entity = new ExcelModelEntity(taskType, fileName, sheetName, headList, widthList, titleHeight, contentHeight);
        entity.setExcludeFieldList(excludeFieldList);
        entity.setAlignmentList(alignmentList);
        // 提取样例
        if (isExample) {
            entity.setExampleList(Collections.singletonList(exampleList));
            entity.setRemark(LanguageEnum.isCn() ? excelModel.remark() : (StringUtils.isBlank(excelModel.remarkEn()) ? excelModel.remark() : excelModel.remarkEn()));
            entity.setRemarkHeight(excelModel.remarkHeight());
        }

        return entity;
    }

    /**
     * 提取并排序Excel列字段
     *
     * @param fields           类的所有字段数组
     * @param excludeFieldList 要排除的字段名列表，优先级高于includeFieldList
     * @param includeFieldList 要包含的字段名列表，与excludeFieldList互斥
     * @return 经过过滤和排序的Excel列字段列表
     */
    public static List<Field> extractSortFields(Field[] fields, List<String> excludeFieldList, List<String> includeFieldList) {
        List<Field> fieldList = Arrays.asList(fields);
        fieldList = fieldList.stream().filter(f -> {
            if (f.getAnnotation(ExcelColumn.class) == null) {
                return false;
            }
            // 排除字段
            if (CollectionUtils.isNotEmpty(excludeFieldList)) {
                return !excludeFieldList.contains(f.getName());
            }
            // 包含字段
            if (CollectionUtils.isNotEmpty(includeFieldList)) {
                return includeFieldList.contains(f.getName());
            }
            return true;
        }).collect(Collectors.toList());
        fieldList.sort((o1, o2) -> {
            ExcelColumn excelColumn1 = o1.getAnnotation(ExcelColumn.class);
            ExcelColumn excelColumn2 = o2.getAnnotation(ExcelColumn.class);
            int index1 = excelColumn1.index();
            int index2 = excelColumn2.index();
            return index1 - index2;
        });
        return fieldList;
    }

    /**
     * 获取Excel文件名
     *
     * @param excelModel Excel模型注解，包含文件名配置
     * @return 完整的Excel文件名（包含后缀）
     */
    private static String getFileName(ExcelModel excelModel) {
        String fileName = LanguageEnum.isCn() ? excelModel.fileName() : excelModel.fileNameEn();
        if (StringUtils.isBlank(fileName)) {
            fileName = LanguageEnum.isCn() ? excelModel.sheetName() : excelModel.sheetNameEn();
        }
        if (StringUtils.isBlank(fileName)) {
            fileName = (LanguageEnum.isCn() ? "未命名-" : "unnamed-") + DateUtil.getCurrentTime(DateUtil.PATTERN_STANDARD14W);
        }
        if (!fileName.contains(".")) {
            fileName = fileName + ExcelTypeEnum.XLSX.getValue();
        }
        return fileName;
    }

    /**
     * 获取Excel工作表名称
     *
     * @param excelModel Excel模型注解，包含Sheet名称配置
     * @return Sheet名称（根据语言环境返回中文或英文）
     */
    private static String getSheetName(ExcelModel excelModel) {
        return LanguageEnum.isCn() ? excelModel.sheetName() : excelModel.sheetNameEn();
    }
}