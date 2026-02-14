e签宝
# 入职流程电子签署与OCR识别技术实现详解

## 概述

本文档详细介绍了HR系统中入职流程集成e签宝电子签署平台和OCR识别服务的技术实现方案。系统实现了从实名认证、合同生成、签署流程创建到证件智能识别的全链路自动化。

## 技术架构

### 整体架构图

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   移动端/PC端    │    │    HR系统       │    │   第三方服务     │
│                │    │                │    │                │
│ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │ 实名认证页面 │ │◄──►│ │SignatureService│ │◄──►│ │  e签宝平台   │ │
│ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │
│                │    │                │    │                │
│ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │ 证件拍照上传 │ │◄──►│ │ OcrApiService │ │◄──►│ │  OCR服务     │ │
│ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │
│                │    │                │    │                │
│ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │ 合同签署页面 │ │◄──►│ │合同生成服务   │ │◄──►│ │  PS系统      │ │
│ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 电子签署平台集成

### 1. 核心服务架构

#### 主要服务类

| 服务类 | 文件路径 | 主要功能 |
|--------|----------|----------|
| SignatureService | `corehrpt-empl/corehrweb-empl-ext/src/main/java/com/oppo/corehrpt/empl/ext/service/impl/SignatureService.java` | 电子签署核心服务 |
| ESignRequestUtils | `corehrpt-empl/corehrweb-empl-ext/src/main/java/com/oppo/corehrpt/empl/ext/util/ESignRequestUtils.java` | e签宝API调用工具 |
| RecHireGenerateContractService | `corehrpt-empl/corehrweb-empl-ext/src/main/java/com/oppo/corehrpt/empl/ext/service/impl/RecHireGenerateContractService.java` | 合同生成服务 |

#### API控制器

| 控制器 | 路径 | 功能 |
|--------|------|------|
| SignatureController | `/api/v2/esign/` | 签署相关API |
| CommonOcrV3Controller | `/api/v3/ocr/` | OCR识别API |
| SignatureRpcController | `/openapi/v2/esign/` | 签署回调API |

### 2. 实名认证实现

#### 2.1 认证流程

```java
/**
 * 获取个人实名认证地址
 * @param agreementId 协议ID
 * @return 认证链接
 */
public String getAuthUrl(Long agreementId) {
    // 1. 获取用户信息
    RecHireMobileLoginUserBO userInfo = RecHireMobileLoginUserHelper.getUserInfo();
    RecHireEmpListDTO empInfo = recHireBaseService.getWithNational(userInfo.getHireId());
    
    // 2. 构建认证参数
    EsignAuthUrlBO authUrlBO = new EsignAuthUrlBO()
        .setAuthType("PSN_TELECOM_AUTHCODE");  // 个人电信认证码
    
    // 3. 设置认证上下文
    String contextId = this.saveAuthProcess(empInfo);
    EsignAuthUrlBO.AuthContextInfo contextInfo = new EsignAuthUrlBO.AuthContextInfo()
        .setContextId(contextId)
        .setNotifyUrl(appDomain + authNotifyUrl)     // 认证结果回调
        .setRedirectUrl(appDomain + authRedirectUrl); // 认证完成跳转
    
    // 4. 设置个人信息
    EsignAuthUrlBO.AuthIndivInfo indivInfo = new EsignAuthUrlBO.AuthIndivInfo()
        .setCertNo(empInfo.getNationalId())
        .setCertType(EsignCertTypeEnum.INDIVIDUAL_CH_IDCARD.name())
        .setMobileNo(empInfo.getPhone())
        .setName(empInfo.getRealName());
    
    // 5. 调用e签宝公有云API获取认证链接
    return eSignRequestUtils.postPublic(getAuthUrlPath, authUrlBO, JSONObject.class)
        .getString("shortLink");
}
```

#### 2.2 认证状态管理

```java
/**
 * 认证流程入库
 */
public String saveAuthProcess(RecHireEmpListDTO empInfo) {
    String contextId = RandomUtils.uuid();
    RecHireAuthStatusPO authStatus = new RecHireAuthStatusPO()
        .setAuthState(2)        // 2-处理中
        .setHireId(empInfo.getHireId())
        .setPhone(empInfo.getPhone())
        .setUsername(empInfo.getRealName())
        .setCertType("IDCard")
        .setCertNo(empInfo.getNationalId())
        .setContextId(contextId)
        .setCreateDate(new Date());
    
    recHireAuthStatusService.save(authStatus);
    return contextId;
}

/**
 * 认证结果回调处理
 */
public boolean notifyAuthResult(NotifyAuthResultReqBO notifyResult) {
    int authStatus = notifyResult.isSuccess() ? 1 : 0;  // 1-成功，0-失败
    return recHireAuthStatusService.updateByContextId(
        new RecHireAuthStatusPO()
            .setContextId(notifyResult.getContextId())
            .setAuthState(authStatus)
    );
}
```

### 3. 合同生成与签署流程

#### 3.1 签署流程创建

```java
/**
 * 发起签署合同
 * 1. 在e签宝创建账户
 * 2. 上传签署文件
 * 3. 创建签署流程
 */
@Transactional(rollbackFor = Exception.class)
public String getSignUrl(boolean isTem) throws IOException {
    // 1. 检查实名认证状态
    if (!recHireAuthStatusService.checkHaveAuth(hireId)) {
        throw new ServiceException(EmplErrorCodeEnum.ESIGN_USER_NOT_HAVE_AUTH);
    }
    
    // 2. 检查是否已存在签署流程
    List<RecHireSignStatusPO> existingFlows = recHireSignStatusService.selectList(
        new LambdaQueryWrapper<RecHireSignStatusPO>()
            .eq(RecHireSignStatusPO::getHireId, hireId)
            .eq(RecHireSignStatusPO::getSignState, 2)  // 2-处理中
    );
    if (!CollectionUtils.isEmpty(existingFlows)) {
        return existingFlows.get(0).getSignUrl();
    }
    
    // 3. 创建e签宝账户
    String accountId = this.createEsignAccount(empInfo);
    
    // 4. 上传签署文件
    List<SignFileUploadBO> uploadedFiles = this.uploadSignFiles(empInfo);
    
    // 5. 创建签署流程
    CreateSignFlowResultBO signFlowResult = this.createSignFlow(
        empInfo, accountId, uploadedFiles, isTem);
    
    return signFlowResult.getSignUrls().get(0).getSignUrl();
}
```

#### 3.2 e签宝账户管理

```java
/**
 * 创建e签宝外部账号
 */
private String createEsignAccount(RecHireEmpListDTO empInfo) throws IOException {
    String licenseNumber = empInfo.getNationalId();
    String mobileNo = empInfo.getPhone();
    String name = empInfo.getRealName();
    
    // 1. 查询是否已存在账户
    EsignPrivateResult listResult = eSignRequestUtils.getPrivate(
        listOutAccountPath + "?pageIndex=1&pageSize=10&licenseNumber=" + licenseNumber);
    
    JSONArray accounts = JSON.parseArray(listResult.getData().get("accounts").toString());
    if (accounts != null && accounts.size() > 0) {
        JSONObject account = accounts.getJSONObject(0);
        String accountId = account.getString("accountId");
        
        // 2. 检查账户信息是否一致
        if (name.equals(account.getString("name")) && 
            mobileNo.equals(account.getString("mobile"))) {
            return accountId;  // 信息一致，直接返回
        } else {
            // 3. 信息不一致，尝试删除或更新账户
            this.deleteOrUpdateAccount(accountId, mobileNo);
        }
    }
    
    // 4. 创建新账户
    CreateOuterAccountBO createAccount = new CreateOuterAccountBO()
        .setContactsMobile(mobileNo)
        .setLicenseNumber(licenseNumber)
        .setLicenseType("IDCard")
        .setLoginMobile(mobileNo)
        .setName(name);
    
    JSONObject response = eSignRequestUtils.postPrivate(createAccountPath, createAccount, JSONObject.class);
    return response.getString("accountId");
}
```

#### 3.3 文件上传与签署流程配置

```java
/**
 * 上传待签署合同文件
 */
private List<SignFileUploadBO> uploadSignFiles(RecHireEmpListDTO empInfo) throws IOException {
    // 1. 获取待签署文件列表
    List<RecHireSignFileWithCoordinateDTO> signFiles = 
        recHireSignFileService.selectByHireId(empInfo.getHireId());
    
    List<SignFileUploadBO> uploadedFiles = new ArrayList<>();
    String tmpFilePath = FileUtils.getUserDirectoryPath() + File.separator + empInfo.getHireId();
    
    try {
        for (RecHireSignFileWithCoordinateDTO signFile : signFiles) {
            // 2. 下载文件到本地临时目录
            Response fileResponse = commonRpcApi.downloadFile(
                signFile.getUnSignFileId(), 1, empInfo.getEmplid());
            
            String localFilePath = tmpFilePath + File.separator + signFile.getUnSignFilename();
            this.saveFileToLocal(fileResponse, localFilePath);
            
            // 3. 上传文件到e签宝
            JSONObject uploadResult = eSignRequestUtils.uploadFile(
                uploadFilePath, localFilePath, JSONObject.class);
            
            // 4. 构建签署文件信息
            uploadedFiles.add(new SignFileUploadBO()
                .setId(signFile.getId())
                .setFilename(signFile.getUnSignFilename())
                .setFileKey(uploadResult.getString("fileKey"))
                .setCompany(signFile.getCompany())
                .setSealType(signFile.getSealType())
                .setSignTemplateConfigDTOList(signFile.getSignTemplateConfigs())
            );
        }
    } finally {
        // 清理临时文件
        FileUtils.deleteDirectory(new File(tmpFilePath));
    }
    
    return uploadedFiles;
}
```

#### 3.4 签署流程创建详细实现

```java
/**
 * 创建签署流程
 */
private CreateSignFlowResultBO createSignFlow(RecHireEmpListDTO empInfo, String accountId,
                                            List<SignFileUploadBO> uploadedFiles, boolean isTem) {
    // 1. 保存签署状态记录
    String bizNo = this.saveSignStatus(empInfo, accountId, uploadedFiles);
    
    // 2. 构建签署流程参数
    CreateSignFlowBO signFlowBO = new CreateSignFlowBO()
        .setBizNo(bizNo)
        .setCallbackUrl(pcDomain + signNotifyUrl)    // 签署结果回调
        .setRedirectUrl(appDomain + signRedirectUrl) // 签署完成跳转
        .setSubject("员工入职签署");
    
    // 3. 设置签署文档
    List<CreateSignFlowBO.SignDocBO> signDocs = uploadedFiles.stream()
        .map(file -> new CreateSignFlowBO.SignDocBO()
            .setDocName(file.getFilename())
            .setDocFilekey(file.getFileKey()))
        .collect(Collectors.toList());
    signFlowBO.setSignDocs(signDocs);
    
    // 4. 创建签署人列表
    List<CreateSignFlowBO.SignerBO> signers = new ArrayList<>();
    
    // 4.1 员工签署人
    CreateSignFlowBO.SignerBO staffSigner = this.createStaffSigner(uploadedFiles, accountId);
    signers.add(staffSigner);
    
    // 4.2 公司签署人（盖章）
    CreateSignFlowBO.SignerBO companySigner = this.createCompanySigner(empInfo, uploadedFiles);
    if (companySigner != null) {
        signers.add(companySigner);
    }
    
    signFlowBO.setSigners(signers);
    
    // 5. 设置发起人信息
    EsignInitiatorBO initiator = this.querySignInitiator(
        companySigner != null ? companySigner.getUniqueId() : "", empInfo.getBusId());
    signFlowBO.setInitiatorName(initiator.getInitiatorName())
              .setInitiatorMobile(initiator.getInitiatorMobile())
              .setInitiatorOrganizeNo(initiator.getInitiatorOrganizeNo());
    
    // 6. 调用e签宝API创建签署流程
    return eSignRequestUtils.postPrivate(createSignFlowsPath, signFlowBO, CreateSignFlowResultBO.class);
}
```

### 4. 签署回调处理

#### 4.1 回调接口实现

```java
/**
 * 签署结果回调处理
 */
public boolean notifySignResult(NotifySignResultReqBO notifyResult) {
    // 1. 参数校验
    if (StringUtils.isBlank(notifyResult.getAction()) || 
        notifyResult.getStatus() == null ||
        StringUtils.isBlank(notifyResult.getBizNo())) {
        throw new ValidatorException(ValidatorErrorCodeEnum.PARAMETER_ERROR);
    }
    
    // 2. 只处理完成回调
    if (!"SIGN_FLOW_FINISH".equals(notifyResult.getAction())) {
        return true;
    }
    
    // 3. 查询签署状态记录
    RecHireSignStatusPO signStatus = recHireSignStatusService.getByBizNo(notifyResult.getBizNo());
    if (signStatus == null || notifyResult.getStatus() != 2) {
        return true;
    }
    
    try {
        // 4. 检查业务状态，避免重复处理
        RecHireBasePO hireBase = recHireBaseService.getByHireId(signStatus.getHireId());
        if (this.isAlreadyProcessed(hireBase)) {
            return true;
        }
        
        // 5. 更新签署状态
        int signStatusCode = this.getSignStatusCode(notifyResult.getStatus());
        signStatus.setSignState(signStatusCode).setUpdateDate(new Date());
        recHireSignStatusService.updateById(signStatus);
        
        // 6. 签署成功后异步下载签署完成的文件
        if (signStatusCode == 1) {
            recHireContractServiceAsync.downloadSignFile(
                notifyResult.getFinishDocUrlBeans(), signStatus.getHireId());
        }
        
        return true;
    } catch (Exception e) {
        log.error("notifySignResult error: {}", e.getMessage(), e);
        return false;
    }
}

private int getSignStatusCode(int esignStatus) {
    switch (esignStatus) {
        case 2: return 1;  // 签署成功
        case 7: return 3;  // 拒绝签署
        case 8: return 0;  // 签署作废
        default: return 0; // 签署失败
    }
}
```

### 5. e签宝配置

#### 5.1 配置文件结构

```yaml
esign:
  projectId: 1000030
  # 私有云配置（用于签署流程管理）
  private:
    url: https://t-esign.myoas.com
    secret: fd0661e1669bc4cc69f3874976146c55
    path:
      createAccount: /V1/accounts/outerAccounts/create      # 创建外部账户
      createSignFlows: /V1/signFlows/create                # 创建签署流程
      uploadFile: /V1/files/upload                         # 上传文件
      listOutAccount: /V1/accounts/outerAccounts/list      # 查询外部账户
      deleteOutAccount: /V1/accounts/outerAccounts/delete  # 删除外部账户
      updateOutAccount: /V1/accounts/outerAccounts/update  # 更新外部账户
      cancelSignFlows: /V1/signFlows/cancel               # 取消签署流程
      listInnerAccount: /V1/accounts/innerAccounts/list   # 查询内部账户
  
  # 公有云配置（用于实名认证）
  public:
    url: https://smlopenapi.esign.cn
    appId: 4438793278
    secret: d76588b91ffd6abbe03b36781deba8e2
    path:
      accessToken: /v1/oauth2/access_token?appId={0}&secret={1}&grantType=client_credentials
      getAuthUrl: /v2/identity/auth/web/indivAuthUrl       # 获取实名认证地址
  
  # 回调地址配置
  url:
    sign:
      notify: /corehrpt-common/openapi/v2/esign/sign/notify    # 签署结果回调
      redirect: /app/{0}/sign-success?token={1}               # 签署完成跳转
    auth:
      notify: /corehrpt-empl/openapi/v2/esign/auth/notify     # 认证结果回调
      redirect: /app/{0}/auth-success?token={1}               # 认证完成跳转
```

## OCR识别服务集成

### 1. OCR服务架构

#### 1.1 核心服务类

```java
/**
 * OCR 图片识别核心服务
 */
@Service
public class OcrApiService {
    private final static String OCR_API_URL = "/EBD-DMP-OCR/api";
    
    @Autowired
    private Apimarket apimarket;  // GSM API调用客户端
    
    /**
     * 身份证识别
     */
    public OcrIdCard recognitionIdCard(MultipartFile imageFile, String fileId, String oprId) {
        try {
            OcrResponse<OcrIdCard> response = ocr(OcrType.ID_CARD, imageFile, fileId, 
                new TypeReference<>() {}, oprId);
            return response == null ? null : response.getDetails();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("OCR身份证识别失败: {}", e.getMessage(), e);
            throw new ServiceException(EmplErrorCodeEnum.OCR_HANDLE_ERROR);
        }
    }
    
    /**
     * 银行卡识别
     */
    public OcrBankCard recognitionBankCard(MultipartFile imageFile, String fileId, String oprId) {
        try {
            OcrResponse<OcrBankCard> response = ocr(OcrType.BANK_CARD, imageFile, fileId,
                new TypeReference<>() {}, oprId);
            return response == null ? null : response.getDetails();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("OCR银行卡识别失败: {}", e.getMessage(), e);
            throw new ServiceException(EmplErrorCodeEnum.OCR_HANDLE_ERROR);
        }
    }
}
```

#### 1.2 OCR类型枚举

```java
public enum OcrType {
    ID_CARD("id_card", "70104"),      // 身份证
    BANK_CARD("bank_card", "80501");  // 银行卡
    
    private final String type;
    private final String code;
    
    OcrType(String type, String code) {
        this.type = type;
        this.code = code;
    }
}
```

### 2. 身份证识别实现

#### 2.1 API接口设计

```java
/**
 * 身份证OCR识别接口（V3版本）
 */
@RestController
@RequestMapping("/api/v3/ocr")
public class CommonOcrV3Controller {
    
    @PostMapping("/id-card")
    @ApiOperation("身份证OCR识别")
    @UserAuthorization
    public ResultVo<OcrIdCard> recognitionIdCard(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "fileId", required = false) String fileId) {
        
        // 1. 文件格式校验
        if (!this.isValidImageFile(file)) {
            throw new ServiceException(EmplErrorCodeEnum.INVALID_IMAGE_FORMAT);
        }
        
        // 2. 调用OCR识别
        String oprId = UserAuthorizationHelper.getUserInfo().getEmplId();
        OcrIdCard result = ocrApiService.recognitionIdCard(file, fileId, oprId);
        
        return ResultVo.success(result);
    }
    
    private boolean isValidImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && (
            contentType.startsWith("image/jpeg") ||
            contentType.startsWith("image/png") ||
            contentType.startsWith("image/jpg")
        );
    }
}
```

#### 2.2 身份证识别结果实体

```java
/**
 * 身份证OCR识别结果
 */
@Data
public class OcrIdCard implements Serializable {
    @ApiModelProperty("户籍地址")
    private String address;
    
    @ApiModelProperty("出生日期")
    private String birthDate;
    
    @ApiModelProperty("公民身份号码")
    private String code;
    
    @ApiModelProperty("证件失效日期")
    private String expireDate;
    
    @ApiModelProperty("性别")
    private String gender;
    
    @ApiModelProperty("证件签发日期")
    private String issueDate;
    
    @ApiModelProperty("签发机关")
    private String issuer;
    
    @ApiModelProperty("姓名")
    private String name;
    
    @ApiModelProperty("民族")
    private String nationality;
}
```

#### 2.3 身份证信息业务处理

```java
/**
 * 身份证信息提交与校验服务
 */
@Service
public class RecHireNidServiceImpl {
    
    /**
     * 提交身份证信息
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean submitNidInfo(RecHireNidSubmitDTO submitDTO) {
        // 1. 基础校验
        this.validateBasicInfo(submitDTO);
        
        // 2. 证件号码格式校验
        this.validateNationalIdFormat(submitDTO.getNationalId(), submitDTO.getNationalIdType());
        
        // 3. 在途/在职校验
        this.validateEmploymentStatus(submitDTO.getNationalId());
        
        // 4. 黑名单/冷静期校验
        this.validateBlacklistAndCoolingPeriod(submitDTO.getNationalId());
        
        // 5. 海外敏感字段处理
        if (this.isOverseasEmployee(submitDTO)) {
            this.mapNationalId(submitDTO);
        }
        
        // 6. 保存身份证信息
        this.saveNationalIdInfo(submitDTO);
        
        // 7. 处理身份证附件
        this.handleIdCardAttachments(submitDTO);
        
        return true;
    }
    
    /**
     * 证件号码格式校验
     */
    private void validateNationalIdFormat(String nationalId, String nationalIdType) {
        if ("CHN18".equals(nationalIdType)) {
            // 中国身份证18位校验
            if (!Pattern.matches("^[1-9]\\d{5}(18|19|20)\\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$", nationalId)) {
                throw new ServiceException(EmplErrorCodeEnum.INVALID_ID_CARD_FORMAT);
            }
        } else if ("IDENC".equals(nationalIdType)) {
            // 印度Aadhaar Card 12位数字校验
            if (!Pattern.matches("^\\d{12}$", nationalId)) {
                throw new ServiceException(EmplErrorCodeEnum.INVALID_AADHAAR_FORMAT);
            }
        }
    }
    
    /**
     * 海外敏感字段映射
     */
    private void mapNationalId(RecHireNidSubmitDTO submitDTO) {
        // 根据不同国家/地区映射敏感字段
        String mappedValue = sensitiveFieldMapper.mapNationalId(
            submitDTO.getNationalId(), 
            submitDTO.getCountryCode()
        );
        submitDTO.setNationalId(mappedValue);
    }
}
```

### 3. 银行卡识别实现

#### 3.1 银行卡识别结果实体

```java
/**
 * 银行卡OCR识别结果
 */
@Data
public class OcrBankCard implements Serializable {
    @ApiModelProperty("银行卡号")
    private String cardNumber;
    
    @ApiModelProperty("银行名称")
    private String bankName;
    
    @ApiModelProperty("卡类型")
    private String cardType;
    
    @ApiModelProperty("信用卡组织")
    private String creditCardOrganization;
    
    @ApiModelProperty("有效期")
    private String effectiveDate;
    
    @ApiModelProperty("发卡日期")
    private String issueDate;
    
    @ApiModelProperty("持卡人姓名")
    private String name;
}
```

#### 3.2 银行卡信息业务处理

```java
/**
 * 银行卡信息提交与校验服务
 */
@Service
public class RecHireBankServiceImpl {
    
    /**
     * 提交银行卡信息
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean submitBankInfo(RecHireBankSubmitDTO submitDTO) {
        // 1. 基础信息校验
        this.validateBankInfo(submitDTO);
        
        // 2. 银行卡账户名与真实姓名匹配校验
        this.validateAccountNameMatch(submitDTO);
        
        // 3. 保存银行卡信息
        this.saveBankCardInfo(submitDTO);
        
        // 4. 处理银行卡附件
        this.handleBankCardAttachment(submitDTO);
        
        return true;
    }
    
    /**
     * 银行卡账户名匹配校验
     */
    private void validateAccountNameMatch(RecHireBankSubmitDTO submitDTO) {
        RecHireBasePO hireBase = recHireBaseService.getByHireId(submitDTO.getHireId());
        String realName = StringUtils.isNotBlank(hireBase.getRealName()) ? 
            hireBase.getRealName() : hireBase.getName();
        
        if (!realName.equals(submitDTO.getAccountName())) {
            throw new ServiceException(EmplErrorCodeEnum.BANK_ACCOUNT_NAME_MISMATCH);
        }
    }
}
```

### 4. OCR核心处理逻辑

#### 4.1 统一OCR调用方法

```java
/**
 * 统一OCR识别处理方法
 */
private <T> OcrResponse<T> ocr(OcrType ocrType, MultipartFile imageFile, String fileId,
                               TypeReference<OcrResponse<T>> typeReference, String oprId) {
    // 1. 构建请求URL
    String url = gsmUrl + OCR_API_URL;
    
    // 2. 准备请求参数
    Map<String, Object> param = new HashMap<>();
    param.put("type", ocrType.getCode());
    
    // 3. 处理图片文件
    File tempFile = null;
    try {
        tempFile = File.createTempFile("ocr_temp", ".png");
        imageFile.transferTo(tempFile);
        param.put("image_file", tempFile);
    } catch (IOException e) {
        log.error("图片文件处理失败", e);
        throw new SimpleException("图片文件处理失败");
    }
    
    // 4. 设置请求头
    Map<String, String> headers = new HashMap<>();
    headers.put("envid", env);  // 环境标识
    
    // 5. 调用GSM API
    log.info("OCR请求参数: type={}, envid={}", ocrType.getCode(), env);
    ApimarketResponseMessage response = apimarket.postForm(url, param, headers);
    
    // 6. 处理响应结果
    String responseData = this.consumeResponse(response, fileId, tempFile, oprId);
    
    // 7. 解析JSON响应
    JSONArray jsonArray = JSON.parseArray(responseData);
    if (jsonArray.isEmpty()) {
        return null;
    }
    
    String resultJson = jsonArray.getString(0);
    return JSONObject.parseObject(resultJson, typeReference);
}
```

#### 4.2 响应结果处理与错误处理

```java
/**
 * 处理OCR API响应结果
 */
private String consumeResponse(ApimarketResponseMessage response, String fileId, 
                              File tempFile, String oprId) {
    // 1. 检查HTTP响应状态
    if (!response.isSuccessful()) {
        throw new SimpleException("OCR API调用失败: " + response.getErrorResponseAsString());
    }
    
    try {
        // 2. 解析响应JSON
        String responseContent = response.getContentString();
        JSONObject jsonObject = JSON.parseObject(responseContent);
        String code = jsonObject.getString("code");
        
        // 3. 处理业务错误码
        if (!"0".equals(code)) {
            if ("OCR-0004".equals(code)) {
                // 特殊处理：加密文件错误
                this.handleEncryptedFileError(tempFile, oprId);
            } else {
                throw new SimpleException("OCR识别失败，错误码: " + code);
            }
        }
        
        // 4. 返回识别结果数据
        return jsonObject.getString("data");
        
    } catch (IOException e) {
        log.error("OCR响应解析失败: {}", e.getMessage(), e);
        throw new SimpleException("OCR响应解析失败");
    } finally {
        // 5. 清理临时文件
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }
}

/**
 * 处理加密文件错误
 */
private void handleEncryptedFileError(File tempFile, String oprId) {
    try {
        // 1. 上传文件到文件服务获取文件信息
        ResultVo<GBassFilePO> uploadResult = commonRpcApi.uploadFile(
            FileUtil.fileToMultipartFile(tempFile), 0, "", "", null);
        
        if (uploadResult == null || uploadResult.getData() == null) {
            throw new ServiceException(EmplErrorCodeEnum.OCR_RECOGNITION_ERROR);
        }
        
        // 2. 检查文件是否加密
        String gbassFileId = uploadResult.getData().getGbassFileId();
        ResultVo<FSFileInfo> fileInfoResult = commonRpcApi.getFileInfoById(gbassFileId, oprId);
        
        if (fileInfoResult.getData() != null && 
            !Objects.equals(0, fileInfoResult.getData().getSecuredType())) {
            // 文件已加密，抛出专门的错误
            throw new ServiceException(EmplErrorCodeEnum.OCR_ONLY_SUPPORT_NOT_ENCRYPTED_IMAGES);
        } else {
            // 文件未加密但识别失败
            throw new ServiceException(EmplErrorCodeEnum.OCR_RECOGNITION_ERROR);
        }
        
    } catch (Exception e) {
        log.error("处理加密文件错误失败: {}", e.getMessage(), e);
        throw new ServiceException(EmplErrorCodeEnum.OCR_RECOGNITION_ERROR);
    }
}
```

### 5. OCR响应数据结构

#### 5.1 通用响应包装类

```java
/**
 * OCR识别响应包装类
 */
@Data
public class OcrResponse<T> implements Serializable {
    @ApiModelProperty("文档顺时针旋转方向")
    private Integer orientation;
    
    @ApiModelProperty("文档所在文件页号，从0开始")
    private Integer page;
    
    @ApiModelProperty("文档类型")
    private String type;
    
    @ApiModelProperty("识别详情（身份证或银行卡信息）")
    private T details;
}
```

## 业务流程集成

### 1. 入职流程整体设计

```
┌─────────────────┐
│  候选人注册      │
└─────┬───────────┘
      │
┌─────▼───────────┐
│  身份证OCR识别   │ ◄─── OCR服务
└─────┬───────────┘
      │
┌─────▼───────────┐
│  实名认证        │ ◄─── e签宝公有云
└─────┬───────────┘
      │
┌─────▼───────────┐
│  银行卡OCR识别   │ ◄─── OCR服务
└─────┬───────────┘
      │
┌─────▼───────────┐
│  合同生成        │ ◄─── PS系统
└─────┬───────────┘
      │
┌─────▼───────────┐
│  电子签署        │ ◄─── e签宝私有云
└─────┬───────────┘
      │
┌─────▼───────────┐
│  入职完成        │
└─────────────────┘
```

### 2. 关键业务节点

#### 2.1 证件信息采集与校验

1. **OCR识别**：自动提取身份证、银行卡信息
2. **格式校验**：验证证件号码格式的合法性
3. **业务校验**：在途/在职状态、黑名单、冷静期检查
4. **海外处理**：敏感字段映射和特殊处理

#### 2.2 实名认证流程

1. **认证发起**：调用e签宝公有云获取认证链接
2. **用户认证**：用户在e签宝页面完成实名认证
3. **结果回调**：e签宝异步通知认证结果
4. **状态更新**：更新用户认证状态

#### 2.3 电子签署流程

1. **账户创建**：在e签宝创建或更新外部账户
2. **文件上传**：上传待签署的合同文件
3. **流程创建**：创建包含员工和公司的签署流程
4. **签署执行**：用户在e签宝页面完成签署
5. **结果处理**：异步下载签署完成的文件

### 3. 错误处理与异常管理

#### 3.1 OCR识别异常

```java
public enum EmplErrorCodeEnum {
    OCR_HANDLE_ERROR("OCR_001", "OCR识别处理失败"),
    OCR_RECOGNITION_ERROR("OCR_002", "OCR识别失败"),
    OCR_ONLY_SUPPORT_NOT_ENCRYPTED_IMAGES("OCR_003", "OCR仅支持非加密图片"),
    INVALID_IMAGE_FORMAT("OCR_004", "不支持的图片格式");
}
```

#### 3.2 电子签署异常

```java
public enum EmplErrorCodeEnum {
    ESIGN_USER_NOT_HAVE_AUTH("ESIGN_001", "用户未完成实名认证"),
    COMPANY_MAPPING_NOT_FOUND("ESIGN_002", "未找到公司印章配置"),
    SIGN_FLOW_CREATE_FAILED("ESIGN_003", "签署流程创建失败");
}
```

## 安全与性能优化

### 1. 安全措施

#### 1.1 数据加密
- 敏感字段（身份证号、银行卡号）采用加密存储
- 海外员工敏感信息进行字段映射处理

#### 1.2 访问控制
- API接口采用用户认证和授权机制
- 回调接口进行签名验证

#### 1.3 文件安全
- 临时文件及时清理
- 支持加密文件检测和处理

### 2. 性能优化

#### 2.1 异步处理
- 签署完成后异步下载文件
- 大文件上传采用分片处理

#### 2.2 缓存策略
- e签宝账户信息缓存
- OCR识别结果缓存

#### 2.3 错误重试
- API调用失败自动重试
- 文件上传失败重试机制

## 监控与运维

### 1. 日志记录

```java
// OCR识别日志
log.info("OCR请求参数: type={}, envid={}", ocrType.getCode(), env);
log.info("OCR响应结果: {}", responseData);

// 电子签署日志
log.info("创建签署流程请求: {}", createSignFlowBO);
log.info("签署回调通知: {}", notifySignResultReqBO);
```

### 2. 关键指标监控

- OCR识别成功率和响应时间
- 实名认证成功率
- 签署流程完成率
- API调用失败率

### 3. 告警机制

- OCR服务异常告警
- e签宝服务异常告警
- 业务流程异常告警

## 总结

本系统通过集成e签宝电子签署平台和OCR识别服务，实现了入职流程的全链路数字化：

1. **OCR智能识别**：支持身份证、银行卡等证件的自动识别，提高信息录入效率
2. **实名认证**：通过e签宝公有云实现可靠的身份认证
3. **电子签署**：基于e签宝私有云实现合同的在线签署
4. **全流程自动化**：从证件识别到合同签署的端到端自动化处理

系统具备良好的扩展性、安全性和稳定性，为企业数字化转型提供了强有力的技术支撑。

---

**文档版本**: v1.0  
**创建日期**: 2025-02-09  
**作者**: Wenjie.Xiang


```JSON
{
  "code" : "0",
  "msg" : "ok",
  "data" : [ {
    "details" : {
      "issuer" : "上饶县公安局",
      "issue_date" : "2018.06.05",
      "expire_date" : "2028.06.05"
    },
    "orientation" : 90,
    "image_size" : [ 1440, 720 ],
    "page" : 0,
    "type" : "70104",
    "filename" : "temp1216964077864980058.png"
  } ]
}
```

总结