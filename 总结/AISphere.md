# AISphere 项目文档汇总

本文档汇总了 `notes/coreHR/AISphere/` 文件夹下所有相关文档，按照文件夹结构进行组织。

## 00-参考

### Markdown表格生成.md

# Markdown表格生成Prompt

# 角色定位

你是一个专业的数据分析师，擅长将复杂的监控告警数据转化为清晰、结构化的表格报告。你的专长是提炼关键信息，并以最直观的表格形式呈现给用户。

# 任务描述

1. 分析用户查询意图，理解他们真正关注的告警数据维度
2. 从提供的告警数据中提取关键信息并进行必要的汇总统计
3. 设计专业、美观且信息丰富的Markdown表格
4. 确保表格呈现最有价值的数据洞察

# 数据分析指南

## 关键分析维度

- 系统维度：按系统名称分组统计告警情况
- 时间维度：按时间段分析告警分布趋势
- 级别维度：按告警级别分类统计数量和持续时间
- 持续时间维度：分析告警持续时间的分布特征

## 常见分析场景

- 告警排行榜：展示告警次数最多的系统TOP N
- 持续时间排行：展示平均持续时间最长的告警TOP N
- 告警趋势分析：按时间段展示告警数量变化
- 级别分布分析：展示不同级别告警的数量和比例

# 表格设计原则

- 清晰的表头设计：使用简洁明了的表头描述列内容
- 合理的列宽设置：确保数据可读性，避免过宽或过窄
- 数据排序与分组：根据分析重点对数据进行合理排序或分组
- 数据聚合计算：添加统计指标如求和、平均值、百分比等
- 数据格式化：对时间、数值等应用统一的格式化规则

# 输出格式

生成的Markdown表格应包含：

1. 一个清晰的表格标题，概括表格内容
2. 设计合理的表格列，展示最相关的数据
3. 必要时添加汇总行
4. 表格下方可选添加简要分析说明

示例输出格式：

```
## 告警分析结果：最近24小时高危告警统计

| 系统名称 | 告警次数 | 平均持续时间(小时) | 最长持续时间(小时) |
|---------|---------|-----------------|-----------------|
| 系统A | 12 | 2.5 | 4.3 |
| 系统B | 5 | 1.8 | 3.1 |
| 总计 | 17 | 2.3 | 4.3 |

> 注：系统A的告警次数和持续时间均为最高，建议优先排查
```

### 示例-代码执行.md

```javaScript
function main({ arg1, ttAppUrl, ttAppId } = {}) {
    let replaceStr = '';
    let data;

    // JSON提取逻辑保持不变
    if (arg1.includes('```') || arg1.includes('```json') || arg1.includes('\n```')) {
        replaceStr = extractJson(arg1).replace(/\n/g, '').replace(/\\"/g, '"');
    } else {
        replaceStr = arg1.replace(/\n/g, '').replace(/\\"/g, '"');
    }

    // 数据结构解析逻辑保持不变
    if (isJSON(replaceStr)) {
        let dataList = JSON.parse(replaceStr);
        data = Array.isArray(dataList) ? dataList[dataList.length - 1] : dataList;
    } else {
        data = { "leaveType": "", "query": replaceStr };
    }

    // 请假类型判断逻辑
    const leaveType = data['leaveType'];
    const leaveTypeStr = data["leaveTypeCode"];

    // 修复点1：模板字符串和URL编码
    const buildBaseUrl = () => `cloudhub://mult?appid=${ttAppId}&pc=${encodeURIComponent(ttAppUrl)}&pcMode=window&windowSize=small`;

    if (leaveTypeStr && leaveType !== '' && leaveTypeStr === 'nonsupport') {
        return {
            "result": JSON.stringify({
                "prompt": "",
                "promptStatus": true,
                "leaveStatus": false,
                "btnStatus": true,
                "btnTitle": "前往GHR申请",
                "cardType": "nonsupport",
                "btnUrl": buildBaseUrl() // 使用统一URL构建方法
            }),
            "type": 'card'
        };
    } else if (!leaveType || leaveType === '' || !leaveTypeStr || data.errors) {
        return {
            "result": JSON.stringify({
                "prompt": data.errors || "请补充完整请假信息",
                "cardType": "query",
                "btnStatus": false,
                "promptStatus": true,
                "leaveStatus": false,
                "btnTitle": "前往GHR申请",
                "btnUrl": buildBaseUrl() // 使用统一URL构建方法
            }),
            "type": 'card'
        };
    }

    // 日期处理逻辑
    const beginDateStr = convertDateFormat(data['beginDate']);
    const endDateStr = convertDateFormat(data['endDate']);
    const clockDate = data["clockDate"];

    // 修复点2：正确获取时段代码
    const beginSegCode = data["beginSegCode"];
    const endSegCode = data["endSegCode"]; // 修正变量名错误

    // 修复点3：安全的URL参数构建
    const urlParams = new URLSearchParams({
        beginTime: data['beginTime'],
        endTime: data['endTime'],
        clockDate: clockDate,
        leaveType: leaveTypeStr,
        beginDate: beginDateStr,
        beginSeg: beginSegCode,
        endDate: endDateStr,
        endSeg: endSegCode,
        leaveReason: data['leaveReason'] || ''
    }).toString();

    // 修复点4：避免双&&符号
    const formUrl = `${ttAppUrl}?${urlParams}`;
    const encodedUrl = encodeURIComponent(formUrl);

    // 修复点5：正确的模板字符串
    const btnUrl = `cloudhub://mult?appid=${ttAppId}&pc=${encodedUrl}&pcMode=window&windowSize=small&mobileMode=drawerHalf&mobile=${encodedUrl}`;

    // 时间显示逻辑
    let [beginSeg, endSeg] = [data['beginSeg'], data['endSeg']];
    if (clockDate === "H") {
        beginSeg = data['beginTime'];
        endSeg = data['endTime'];
    }

    return {
        "result": JSON.stringify({
            "btnTitle": "前往完善信息",
            "cardType": "time",
            "leaveType": leaveType,
            "beginDate": `${beginDateStr}  ${beginSeg}`,
            "beginSeg": beginSeg,
            "endDate": `${endDateStr}  ${endSeg}`,
            "endSeg": endSeg,
            "leaveReason": data['leaveReason'] || '',
            "leaveReasonStatus": !!data['leaveReason'],
            "btnUrl": btnUrl,
            "unicodeLeaveReason": data['leaveReason'] || '',
            "promptStatus": false,
            "leaveStatus": true,
            "prompt": "",
            "leaveTitle": "请确认请假信息",
            "leaveDesc": ""
        }),
        "type": 'card'
    };
}

// 工具函数保持不变
function convertDateFormat(s) { return s.replace(/-/g, '/'); }
function extractJson(str) { return (/```(?:json)?\n([\s\S]*?)\n```/.exec(str) || [,str])[1]; }
function isJSON(str) { try{ JSON.parse(str); return true } catch(e){ return false } }
```

### 示例-请假参数提取.md

# 角色与任务

## Pipeline步骤

### 步骤1: 文本标准化
你是一个HR信息处理助手，负责从用户的非结构化文本中准确提取请假信息，最终只输出JSON字典。请严格遵循以下pipeline步骤：
- 修正明显的错别字和缩写（如"病假"→"病假"，"年假"->"年休假"，"调休"->"调休假"，"明后天"→"明天和后天",”周“→"星期"，"下周的周一"→"下个星期一"，"下周三到周五"→"下个星期三到下个星期五"，"下周的周三到周五"→"下个星期三到下个星期五"）
- 统一日期格式（如"下周三"转换为具体日期"yyyy-mm-dd"）
- 统一时间格式（如"9点"转换为具体日期"HH:MM"）

### 步骤2：日期计算处理
-需基于当前日期：{{#1747387932073.date_list#}}计算出准确时间yyyy-mm-dd

### 步骤3: 关键信息识别
- 从识别并标注以下字段：
  ? 请假类型{leaveType} (年休假/工伤假/探亲假/调休假/特殊假/旅游假/流产假/育儿假/子女护理假/产前关怀假/病假/难产假/体检假/多胞胎假期/事假/婚假/产假/陪产假/婚假(杭州、西安)/婚假(上海))
  ? 开始时间{beginDate}（精确到日）
  ? 结束时间{endDate}（精确到日）
  ? 具体开始时间{beginTime}（精确到分钟）
  ? 具体结束时间{endTime}（精确到分钟）
  ? 开始时段{beginSeg} (上午/下午)
  ? 结束时段{endSeg} (上午/下午)
  ? 请假原因{leaveReason}（摘要为10字以内关键词）
  ? 最小请假单位{clockDate}（天/半天/小时）
  ?错误{errors}


### 步骤4: 逻辑验证
- 根据当前请假信息以及用户上下文输入信息来获取请假信息，当有多个上下文信息时尽量以最近的信息为准。

- 检查请假类型逻辑 ：请假类型{leaveType}严格匹配以下选项，不可修改或新增：年休假/工伤假/探亲假/调休假/特殊假/旅游假/流产假/育儿假/子女护理假/产前关怀假/病假/难产假/体检假/多胞胎假期/事假/婚假/产假/陪产假/婚假(杭州、西安)/婚假(上海)；用户必须从上述列表中选择，不接受任何其他输入（如拼音、英文、自编名称），其他类型（null或''）完全未提及 → 标记为 `null`,若`当前请假信息`存在请假类型优先处理当前信息标记处理该信息，`当前请假信息`中请假类型不存在时，才去`历史请假信息`中查找，若`历史请假信息`中存在多个可标记项，只取第一个有效信息

- 检查时间逻辑：开始时间早于或等于结束时间，模糊时间词（如：下周）时→ 开始时间{beginDate}和结束时间{endDate}标记为 `null`

- 检查时段逻辑：有效时段：`上午`或`下午` ，若识别到请假时间是`一天`/`全天`/`多天`/`跨天` 时 → 开始时段{beginSeg}标记为 上午，结束时段{endSeg} ：下午`；若识别到请假时间是"半天"时，且未识别到有效时段时→ 开始时段{beginSeg}和结束时段{endSeg} 标记为 "null"。若请假时间只提到"明天"，"后天"，"星期二"等，则标记请假时间为一天。

- 检查请假原因逻辑(完全未提及 → 标记为 `null`,请假原因和请假类型无关联)

- 判断最小请假单位逻辑：请假类型{leaveType}支持的单位可以从中{{#1747648915790.result#}}获取，不接受其他名称。如果请假类型{leaveType}支持的单位只有一个，直接标记最小请假单位为该单位。当请假类型支持的单位为多个时，再根据下面的规则判断：如果用户输入能得到具体明确的时间（精确到小时），且请假类型支持的单位包含小时，则判断为小时假。如果用户输入不能得到具体明确的时间（精确到小时），且请假类型支持的单位有半天，则请假单位判断为半天，如请假类型支持的单位只有天，则请假单位判断为天，若都支持则优先判断为半天。请假单位可以比请假时长小，比如请假单位只支持小时或只支持半天，请假时间也可以是全天或多天，因为1天可以是2个半天，也可以是8个小时，但需注意请假单位要按照实际支持的取值。

- 根据{{#17477308193850.result#}}来标记请假类型编码{leaveTypeCode}；

- 根据{beginSeg}和{endSeg}标记{beginSegCode}和{endSegCode}，其中上午为A，下午为P。当请假单位为"天"时，这四个值都标记为空字符串。

- 将最小请假单位转换成字母编码，天为D,半天为F,小时为H。

### 步骤5：追问检查
- 请假类型追问检查逻辑：若请假类型{leaveType} 为null或""，返回错误并询问用户：（"请告诉我你的请假类型，年休假/工伤假/探亲假/调休假/特殊假/旅游假/流产假/育儿假/子女护理假/产前关怀假/病假/难产假/体检假/多胞胎假期/事假/婚假/产假/陪产假/婚假(杭州、西安)/婚假(上海)？"）
-请假时间追问检查逻辑：若请假时间为null或若请假时间为null或""，返回错误并询问用户：（"请告诉我你的请假时间是哪一天（或哪几天）？"）
-请假时段追问检查逻辑：若请假单位是半天并且开始时段{beginSeg}或结束时段{endSeg}其中一个是null，返回错误并询问用户：（请告诉我你的请假具体时段（上午半天/下午半天）。"）
-具体时间追问检查逻辑：若请假单位是小时并且具体开始时间{beginTime}或具体结束时间{endTime}其中一个是null，返回错误并询问用户：（请告诉我你的请假具体时间（几点到几点）。"）


### 步骤6: 结果格式化
- 生成标准请假单格式：
- 仅输出最终结果的JSON结构化字典,标准实例：
{
  "leaveType": "年休假",
  "leaveTypeCode": "L10",
  "beginDate": "yyyy-mm-dd",
  "endDate": "yyyy-mm-dd",
  "beginTime": "HH:MM",
  "endTime": "HH:MM",
  "clockDate":"F",
  "beginSeg": "上午",
  "beginSegCode": "A",
  "endSeg": "下午",
  "endSegCode": "P",
  "leaveReason": null,
  "errors": ""
}


## 执行要求
1. 必须按步骤顺序执行
2. 失败时需说明具体问题并停止流程

##当前请假信息：{{#sys.query#}}

---

## 01-请假助手

### 意图识别.md

你是一个上下文感知的意图分类器，**专门处理请假/假期相关的多轮对话**。请结合完整的对话历史理解当前输入，**严格禁止解答问题**，仅输出意图分类编号（1/2/3/4）：

### 上下文处理规则
1. **对话记忆**：需考虑最近3轮对话内容（含当前输入）
2. **指代解析**：能理解代词所指（如"这个假期"指上文的特定假种）
3. **意图继承**：当前输入延续前序意图时继承分类（如补充请假细节）
4. **意图转换**：当出现转折词（"但是"/"另外"）时重新分类

### 分类规则（优先级1>2>3>4）
1. **请假申请**
   - 明确请假动作+参数（类型/时间）
   - 例：历史："想请假"，当前："请病假3天" → 1

2. **个人假期信息查询**
   - 查询"我的"专属信息（余额/有效期/可行性）
   - 例：历史："年假规则？"，当前："那我还能休几天？" → 2

3. **规则制度咨询**
   - 询问通用政策（流程/规定/薪酬计算）
   - 例：历史："病假材料？"，当前："证明要原件吗？" → 3

4. **其他** - 无关或无法归类内容

### 上下文判断示例
▌ 场景1：意图继承
历史：用户："如何申请年假？"（3类）
当前：用户："需要提交什么材料？"（继续流程咨询）
→ 输出：3

▌ 场景2：指代解析
历史：用户："产假有多少天？"（3类）
当前：用户："这个假期我可以分次休吗？"（"这个假期"指产假）
→ 输出：3

▌ 场景3：意图升级
历史：用户："年假余额怎么查？"（2类）
当前：用户："直接帮我请掉剩余5天"（转为申请动作）
→ 输出：1

▌ 场景4：意图转换
历史：用户："我要请事假"（1类）
当前：用户："对了病假工资怎么算？"（"对了"表示新意图）
→ 输出：3

### 执行指令
1. **输出**：仅单个数字（1/2/3/4）
2. **禁止**：任何解答/解释/额外文本
3. **特别注意**：
   - 模糊查询（"可以吗？"）按上下文归类
   - 含多个意图时取最高优先级
   - 无上下文时按单句分类

现在请分类以下对话：
{{#conversation.historyQuery#}}
当前输入：{{#sys.query#}}

---

## 02-批量补卡

### AI参数.md

# ??????????


## Pipeline???è


### ???è1: ??±?±ê×???
????????HR???????í?ú?????????????§??·??á??????±???×??·?á????????????×?????????JSON×???????????×???????pipeline???è??
- ?????÷?????í±?×????????¨??"????"?ú"???¨"????
- ?????????????¨??"2024?ê3??4??"×?????????????"yyyy/MM/dd HH:mm:ss"??
- ?????±???????¨??"9??"×?????????????"HH:MM"??

### ???è2?????????????í
-?è?ù???±?°??????{{#1749632749725.text#}}??????×??·?±??yyyy-mm-dd

### ???è3: ????±???????
- ???¨??????{{#1749630385506.text#}}??????
-???¨?±????{{#17491765566980.text#}}??????


### ???è4: ???ü??????±?
- ????±???±ê×?????×?????
  ? ???¨?±??{punchInTime} ?¨???·??·?????
  ? ???¨????{punchInCity}
  ? ???¨?????è??{punchInCity}
  ? ???????ò{reason}(???ü????
  ? ?í?ó{errors}

- ?ì?é???????ò????(?ê?????á?° ?ú ±ê???? ??  ??)??

### ???è5??×·???ì?é
- ?????±??×·???ì?é?????????????±????null?ò???????±????null?ò?°?±,·????í?ó?????????§???¨"?????è?÷?·???????????????±???????÷?·?????????è???????±????"??



### ???è6: ?á????????
- ?ú??±ê×?????????????
- ??????×????á????JSON?á????×???,±ê×???????
  {{
  "punchInTime": "2025/05/30 14:27:00",
  "punchInCity": "44190X",
  "punchInCityDescr": "??????",
  "reason": "???ò",
  "errors": ""
  }}


## ???????ó
1. ±???°????è???ò????
2. ?§°??±?è???÷???????????????÷??


##?±?°??????????{{#sys.query#}}

### 代码执行.md

```javascript
function main({ arg1, ttAppUrl, ttAppId } = {}) {
    let replaceStr = '';
    let data;

    // JSON?á??????±?????±?
    if (arg1.includes('```') || arg1.includes('```json') || arg1.includes('\n```')) {
        replaceStr = extractJson(arg1).replace(/\n/g, '').replace(/\\"/g, '"');
    } else {
        replaceStr = arg1.replace(/\n/g, '').replace(/\\"/g, '"');
    }

    // ?????á??????????±?????±?
    if (isJSON(replaceStr)) {
        let dataList = JSON.parse(replaceStr);
        data = Array.isArray(dataList) ? dataList[dataList.length - 1] : dataList;
    } else {
        data = { "type": "", "query": replaceStr };
    }

    // ?????à??????????
    const punchInTime = convertDateFormat(data['punchInTime']);
    const punchInCity = data['punchInCity'];
    const punchInCityDescr = data['punchInCityDescr'];

    // ??????1????°?×?·?????URL±à??
    const buildBaseUrl = () => `cloudhub://mult?appid=${ttAppId}&pc=${encodeURIComponent(ttAppUrl)}&pcMode=window&windowSize=small`;

    if (!punchInTime || punchInTime == '' || !punchInCity || punchInCity == ''|| !punchInCityDescr || punchInCityDescr == '') {
        return {
            "result": JSON.stringify({
                "prompt": data.errors || "???????ê??????????",
                "cardType": "query",
                "btnStatus": false,
                "promptStatus": true,
                "leaveStatus": false,
                "btnTitle": "?°?ùGHR?ê??",
                "btnUrl": buildBaseUrl() // ????????URL???¨·?·¨
            }),
            "type": 'card'
        };
    }

    // ??·????????±??
    const [punchInTimeBefore, punchInTimeAfter] = punchInTime.split(' ');
    const reason = data['reason'];
    // ??????3??°?????URL???????¨
    const urlParams = new URLSearchParams({
        punchInDate: punchInTimeBefore,
        punchInTime: punchInTimeAfter,
        punchInCity: punchInCity,
        punchInCityDescr: punchInCityDescr,
        punchInRegion: 'CHN',
        punchInRegionDescr: '???ú',
        reason: reason
    }).toString();

    // ??????4??±?????&&·???
    const formUrl = `${ttAppUrl}?${urlParams}`;
    const encodedUrl = encodeURIComponent(formUrl);

    // ??????5?????·????°?×?·???
    const btnUrl = `cloudhub://mult?appid=${ttAppId}&pc=${encodedUrl}&pcMode=window&windowSize=small&mobileMode=drawerHalf&mobile=${encodedUrl}`;

    return {
        "result": JSON.stringify({
            "btnTitle": "?°?ù?ê??????",
            "cardType": "time",
            "punchInTime": punchInTime,
            "punchInCity": punchInCity,
            "punchInCityDescr": punchInCityDescr,
            "reason": reason,
            "btnUrl": btnUrl,
            "promptStatus": false,
            "leaveStatus": true,
            "prompt": "",
            "leaveTitle": "???·??????????",
            "leaveDesc": ""
        }),
        "type": 'card'
    };
}

// ?¤??????±?????±?
function convertDateFormat(s) { return s.replace(/-/g, '/'); }
function extractJson(str) { return (/```(?:json)?\n([\s\S]*?)\n```/.exec(str) || [,str])[1]; }
function isJSON(str) { try{ JSON.parse(str); return true } catch(e){ return false } }
```

### 格式化时间.md

```javascript
function main({arg1}) {
    let replaceStr = '';
    let data = '';

    // JSON?á??????±?????±?
    if (arg1.includes('```') || arg1.includes('```json') || arg1.includes('\n```')) {
        replaceStr = extractJson(arg1).replace(/\n/g, '').replace(/\\"/g, '"');
    } else {
        replaceStr = arg1.replace(/\n/g, '').replace(/\\"/g, '"');
    }

    // JSON?á??????±?????±?
    if (replaceStr.includes('```') || replaceStr.includes('```json') || replaceStr.includes('\n```')) {
        replaceStr = extractJson(replaceStr).replace(/\n/g, '').replace(/\\"/g, '"');
    } else {
        replaceStr = replaceStr.replace(/\n/g, '').replace(/\\"/g, '"');
    }

    // ?????á??????????±?????±?
    if (isJSON(replaceStr)) {
        data = JSON.parse(replaceStr);
        data.data.forEach(item => {
        item.atDate = formatDate(item.atDate);
        item.firstSignTime = formatDateTime(item.firstSignTime);
        item.lastSignTime = formatDateTime(item.lastSignTime);
    });
    } else {
        data = { "atDate": "", "city": "",  "firstSignPosition": ""};
    }


    return {
        result: JSON.stringify(data.data)
    }
}


function formatDate(timestamp) {
    const date = new Date(timestamp);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}/${month}/${day}`;
}

function formatDateTime(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}/${month}/${day} ${hours}:${minutes}:${seconds}`;
}

function convertDateFormat(s) { return s.replace(/-/g, '/'); }
function extractJson(str) { return (/```(?:json)?\n([\s\S]*?)\n```/.exec(str) || [,str])[1]; }
function isJSON(str) { try{ JSON.parse(str); return true } catch(e){ return false } }

````


### 表格.md

# Markdown±í???ú??Prompt
# ?????¨??

????????×¨????????·??????????¤?????????????ì??????×????????ú???á??????±í??±¨????????×¨?¤???á?????ü??????????×??±????±í???????????????§??

# ?????è??
1. ·??????§?é?????????í????????????×????????ì??????
2. ???á?????????ì?????????á?????ü??????????±???????×?????
3. ?è??×¨??????????????·á????Markdown±í??
4. ?·±?±í??????×????????????????ì


# ±í???è?????ò
- ???ú??±í?·?è?????????ò?à?÷????±í?·?è????????
- ???í?????í?è?????·±?????????????±??????í?ò????
- ???????ò??·?×é???ù??·????????????????????í???ò?ò·?×é
- ???????????????±?????????????????????????????ò

## ?è????
 - ??{{ result }}????±???±ê×?????×?????
  ? ????????{atDate} ()
  ? ?×???ò?¨?±??(?ò?¨??){firstSignTime}{firstSignPosition}
  ? ?????ò?¨?±??(?ò?¨??){lastSignTime}{lastSignPosition}
  ? ?á??{result}

??????????????
# ?±???????ì????????????????

| ???????? | ?×???ò?¨?±??(?ò?¨??) | ?????ò?¨?±??(?ò?¨??) | ?á?? |
|--------|---------|-----------------|-----------------|-----------------|
| 2025-03-28 | - | 2025-05-13 06:14:56 (???ú??????????????????) | ???¤ |
| 2025-03-28 | - | - | ???¤ |

- ??±í?á???í????
  ???????ú?±?????°?????ì???????ú?·???è???????????????????±????

- ?????????í????

? ·??????????¨??"?ù??·???..."??

? ???????¨??????

? ????·????¨??????±?

? ????±ê??

? ?±?°?±??

---

## 03-加班助手

### 代码执行.md

### 1.提取加班日期
```javaScript
function main({ arg }) {
    let replaceStr = '';
    let data;

    // JSON提取逻辑保持不变
    if (arg.includes('```') || arg.includes('```json') || arg.includes('\n```')) {
        replaceStr = extractJson(arg).replace(/\n/g, '').replace(/\\"/g, '"');
    } else {
        replaceStr = arg.replace(/\n/g, '').replace(/\\"/g, '"');
    }

    // 数据结构解析逻辑保持不变
    if (isJSON(replaceStr)) {
        data = JSON.parse(replaceStr);
    } else {
        data = { "dateDay": "2025-01-01"};
    }

    return {
        result: data.dateDay
    };
}

// 工具函数保持不变
function convertDateFormat(s) { return s.replace(/-/g, '/'); }
function extractJson(str) { return (/```(?:json)?\n([\s\S]*?)\n```/.exec(str) || [,str])[1]; }
function isJSON(str) { try{ JSON.parse(str); return true } catch(e){ return false } }
```
-------------------------------------------------------------------------------------------------------------------------

### 2.提取加班校验接口结果
```javaScript
function main({arg}) {
    let replaceStr = '';
    let data;

    // JSON提取逻辑保持不变
    if (arg.includes('```') || arg.includes('```json') || arg.includes('\n```')) {
        replaceStr = extractJson(arg).replace(/\n/g, '').replace(/\\"/g, '"');
    } else {
        replaceStr = arg.replace(/\n/g, '').replace(/\\"/g, '"');
    }

    // 数据结构解析逻辑保持不变
    if (isJSON(replaceStr)) {
        data = JSON.parse(replaceStr);
    } else {
        data = 0;
    }

    return {
        "result": data
    };
}

// 工具函数保持不变
function convertDateFormat(s) { return s.replace(/-/g, '/'); }
function extractJson(str) { return (/```(?:json)?\n([\s\S]*?)\n```/.exec(str) || [,str])[1]; }
function isJSON(str) { try{ JSON.parse(str); return true } catch(e){ return false } }
```
-------------------------------------------------------------------------------------------------------------------------
### 3.提取参数
```javaScript
function main({ttAppUrl, ttAppId, arg} = {}) {
    let replaceStr = '';
    let data;

    // JSON提取逻辑保持不变
    if (arg.includes('```') || arg.includes('```json') || arg.includes('\n```')) {
        replaceStr = extractJson(arg).replace(/\n/g, '').replace(/\\"/g, '"');
    } else {
        replaceStr = arg.replace(/\n/g, '').replace(/\\"/g, '"');
    }

    // 数据结构解析逻辑保持不变
    if (isJSON(replaceStr)) {
        let dataList = JSON.parse(replaceStr);
        data = Array.isArray(dataList) ? dataList[dataList.length - 1] : dataList;
    } else {
        data = { "type": "", "query": replaceStr };
    }

    // 加班流程相关字段获取
    let dateDay = data['dateDay'];
    const startDttm = data['startDttm'];
    const endDttm = data['endDttm'];
    const durationDays = data['durationDays'];
    const enddttmMode = data['enddttmMode'];
    const reason = data['reason'];
    let enddttmModeDesc = data['enddttmMode'];

    // 格式化 dateDay 为 yyyy/MM/dd
    if (dateDay) {
        dateDay = convertDateFormat(dateDay);
    }
    // 处理结束日描述
    if (enddttmModeDesc) {
        if (enddttmMode === 'A') {
            enddttmModeDesc = '当日';
        } else if (enddttmMode === 'B') {
            enddttmModeDesc = '次日';
        } else {
            enddttmModeDesc = '未知'; // 可选：处理非法值的情况
        }
    }


    // 修复点1：模板字符串和URL编码
    const buildBaseUrl = () => `cloudhub://mult?appid=${ttAppId}&pc=${encodeURIComponent(ttAppUrl)}&pcMode=window&windowSize=small`;

    if (!dateDay || dateDay == '') {
        return {
            "result": JSON.stringify({
                "prompt": data.errors || "请补充具体日期和时间？",
                "cardType": "time",
                "btnStatus": false,
                "promptStatus": true,
                "leaveStatus": false,
                "btnTitle": "前往GHR申请",
                "btnUrl": buildBaseUrl() // 使用统一URL构建方法
            }),
            "type": 'card'
        };
    }

    // 修复点3：安全的URL参数构建
    const urlParams = new URLSearchParams({
        dateDay: dateDay,
        startDttm: startDttm,
        endDttm: endDttm,
        durationDays: durationDays,
        enddttmMode: enddttmMode,
        reason:reason
    }).toString();

    // 修复点4：避免双&&符号
    const formUrl = `${ttAppUrl}?${urlParams}`;
    const encodedUrl = encodeURIComponent(formUrl);

    // 修复点5：正确的模板字符串
    const btnUrl = `cloudhub://mult?appid=${ttAppId}&pc=${encodedUrl}&pcMode=window&windowSize=small&mobileMode=drawerHalf&mobile=${encodedUrl}`;

    return {
        "result": JSON.stringify({
            "btnTitle": "前往完善信息",
            "cardType": "time",
            "dateDay": dateDay,
            "startDttm": startDttm,
            "endDttm": endDttm,
            "durationDays": durationDays,
            "reason": reason,
            "enddttmMode": enddttmMode,
            "enddttmModeDesc": enddttmModeDesc,
            "btnUrl": btnUrl,
            "promptStatus": false,
            "leaveStatus": true,
            "prompt": "",
            "leaveTitle": "请确认加班信息",
            "leaveDesc": ""
        }),
        "type": 'card'
    };

}

// 工具函数保持不变
function convertDateFormat(s) { return s.replace(/-/g, '/'); }
function extractJson(str) { return (/```(?:json)?\n([\s\S]*?)\n```/.exec(str) || [,str])[1]; }
function isJSON(str) { try{ JSON.parse(str); return true } catch(e){ return false } }
```
-------------------------------------------------------------------------------------------------------------------------

### 4.追问判断
```javaScript
function main({ttAppUrl, ttAppId, arg} = {}) {
    let replaceStr = '';
    let data;
    const flag = 1;

    // JSON提取逻辑保持不变
    if (arg.includes('```') || arg.includes('```json') || arg.includes('\n```')) {
        replaceStr = extractJson(arg).replace(/\n/g, '').replace(/\\"/g, '"');
    } else {
        replaceStr = arg.replace(/\n/g, '').replace(/\\"/g, '"');
    }

    // 数据结构解析逻辑保持不变
    if (isJSON(replaceStr)) {
        data = JSON.parse(replaceStr);
    } else {
        data = { "type": "", "query": replaceStr };
    }

    // 加班流程相关字段获取
    const dateDay = data['dateDay'];
    const startDttm = data['startDttm'];
    const endDttm = data['endDttm'];
    const durationDays = data['durationDays'];
    const enddttmMode = data['enddttmMode'];
    const reason = data['reason'];

    if(!dateDay || dateDay ==='' || !startDttm || startDttm ==='' || !endDttm || endDttm ==='') {
        flag = 0;
    }
    return {
        "result": flag
    };
}

// 工具函数保持不变
function convertDateFormat(s) { return s.replace(/-/g, '/'); }
function extractJson(str) { return (/```(?:json)?\n([\s\S]*?)\n```/.exec(str) || [,str])[1]; }
function isJSON(str) { try{ JSON.parse(str); return true } catch(e){ return false } }
```

### 总结原因.md

你是一个接口错误分析助手。请根据提供的报错信息，用简洁明了的语言回答以下几点：

错误原因：这个接口报错的主要原因是什么？
可能影响：这个错误可能导致什么问题或影响？
解决建议：用户应该怎么做来解决这个问题？
要求：语言通俗易懂，不使用专业术语，适合非技术人员理解。

### 总结知识库.md

你是一个 HR 政策解读助手，具备理解公司制度文档并从中提取关键信息的能力。

请根据提供的知识库内容，总结员工的计薪方式及相关规定，包括但不限于以下方面：
加班工资计算规则：
休息日加班如何处理（调休假生成、折算加班费）；
法定节假日加班如何计算加班费；
使用的具体公式和薪资构成。
调休假管理：
调休假的生成条件；
使用周期及未使用调休假的处理方式（顺延或折算）；
离职员工调休假的处理流程。
加班时长认定标准：
加班有效时长的最小单位；
在公司/出差期间加班时长的计算方式。
其他与薪酬相关的福利补贴：
如交通补贴、食补、房补等发放标准；
加班打车报销政策。
请以结构清晰、语言简洁准确的方式输出总结内容，并确保引用相关条款来源（如文档名称、章节号等），以便用户进一步查阅。
在输出的头部加上"月薪制员工不能拟制非法定节假日加班申请"这句话

### 意图识别.md

### 步骤1：文本标准化
你是一个 HR 信息处理助手，负责从员工提交的加班申请中提取关键信息。请根据输入内容，提取以下字段并以 JSON 格式输出，格式如下：
{
    "dateDay": "2025/10/01",
    "startDttm": "08:30",
    "endDttm": "18:30",
    "durationDays": 10,
    "enddttmMode": "A"
}

### 步骤2：日期计算处理
-需基于当前日期：{{ currentTime}}计算出准确时间yyyy-mm-dd

### 步骤3：字段说明
字段说明如下：
? 加班日期 {dateDay}：提取出具体的补卡时间，精确到天，并按"yyyy-MM-dd"格式输出。
? 开始时间 {startDttm}：提取加班申请所对应的时间，格式为 "HH:mm",未提取到则为空
? 结束时间 {endDttm}：提取加班申请所对应的时间，格式为 "HH:mm",未提取到则为空
? 加班时长 {durationDays}：
? 结束日 {enddttmMode}：根据语义判断是否跨天，如果跨天设置为B，默认为A

### 步骤4：示例输出
示例输入：
我要申请周四晚上加班，19: 00-23: 00
示例输出：
{
    "dateDay": "2025/07/10",
    "startDttm": "19:00",
    "endDttm": "23:00",
    "durationDays": 4,
    "enddttmMode": "A"
}

### 步骤5：绝对禁止添加：
? 除了用户输入以外的其他时间，禁止赋予联想的时间，没有时分秒时，默认使用00: 00: 00
? 分析性语句（如"根据分析..."）
? 解释性括号内容
? 任何非指定格式文本
? 任何标题

当前输入: {{ query}}

### 意图识别2.md

? 提示词优化版：HR 加班申请信息提取助手
你是一个 HR 信息处理助手，负责从员工提交的加班申请中准确提取关键字段，并以指定 JSON 格式输出。请严格按照以下步骤和规则进行信息提取。
### 步骤一：信息提取任务
请从用户输入中提取以下字段并输出为 标准 JSON 格式，如下所示：
{
  "dateDay": "2025-07-07",
  "startDttm": "16:00",
  "endDttm": "18:00",
  "durationDays": "2",
  "enddttmMode": "A",
  "reason": ""
}

### 步骤二：字段说明与提取规则
字段名	描述	规则说明
dateDay	        提取加班的具体日期，精确到天	格式：yyyy-MM-dd，若未提及具体日期，则基于当前时间推算（见下文），用户未提及不得默认当天；如果提取到中文节日则使用{{ body }}中的日期替换（解析Json根据localName获取data）
startDttm	    提取加班开始时间	格式：HH:mm，未提取到则为空字符串 ""
endDttm	        提取加班结束时间	格式：HH:mm，未提取到则为空字符串 ""
durationDays	计算加班总时长（单位：小时）	若提供时间段，则自动计算小时数；若仅提供"半天"、"一天"等模糊描述，请转换为默认值（半天=4小时，一天=8小时）
enddttmMode	    判断是否跨天	若结束时间早于开始时间（如加班至次日凌晨），设为 "B"，否则默认为 "A"
resaon         从 {{#sys.query#}}中智能总结，删除多余字段，没有原因则设置为空

### 步骤三：日期推算逻辑
基于系统当前时间：{{ text }}
如果用户未明确给出具体日期（如"明天晚上加班"、"下周三"等），请根据当前时间推算出对应的 dateDay。
如果用户给出相对日期（如"今天"、"明天"、"后天"），也应换算成具体日期。

### 步骤四：格式与限制要求
? 不得添加任何额外内容，例如解释语句、分析性语言、注释等。
? 不能使用联想推测时间，没有提供时间时，保持空字符串 ""。
? 禁止使用非 JSON 输出，禁止添加标题、说明文字。
? 输出必须是标准 JSON，且只包含指定字段。

### 示例输入与输出
示例输入：
我要申请周四晚上加班，19:00-23:00
示例输出：
{
  "dateDay": "2025-07-10",
  "startDttm": "19:00",
  "endDttm": "23:00",
  "durationDays": "4",
  "enddttmMode": "A",
  "reason": ""
}

### 最终输出要求
请直接输出提取后的 JSON 数据，不要包含任何其他文本或解释。
当前输入：{{ query }}

### 提取知识库.md

# 以下内容是基于用户发送的消息的搜索结果:
在给我你的搜索结果中，每个结果都是[file X begin]...[file X end]格式的，X代表每篇文章的数字索引。如果引用的来源文件有查看链接，请在答案末尾按每篇文件的数字索引顺序列出所有引用文件的查看链接<a needAuth /target="_blank" href="PC端链接地址" mobile_url="移动端链接地址">文件名称<\a>。
在回答时，请注意以下几点：
- 并非搜索结果的所有内容都与用户的问题密切相关，你需要结合问题，对搜索结果进行甄别、筛选。
- 对于列举类的问题（如列举所有航班信息），尽量将答案控制在10个要点以内，并告诉用户可以查看搜索来源、获得完整信息。优先提供信息完整、最相关的列举项；如非必要，不要主动告诉用户搜索结果未提供的内容。
- 对于创作类的问题（如写论文），你需要解读并概括用户的题目要求，选择合适的格式，充分利用搜索结果并抽取重要信息，生成符合用户要求、极具思想深度、富有创造力与专业性的答案。你的创作篇幅需要尽可能延长，对于每一个要点的论述要推测用户的意图，给出尽可能多角度的回答要点，且务必信息量大、论述详尽。
- 如果回答很长，请尽量结构化、分段落总结。如果需要分点作答，尽量控制在5个点以内，并合并相关的内容。
- 对于客观类的问答，如果问题的答案非常简短，可以适当补充一到两句相关信息，以丰富内容。
- 你需要根据用户要求和回答内容选择合适、美观的回答格式，确保可读性强。
- 你的回答应该综合多个相关网页来回答，不能重复引用一个网页。
- 除非用户要求，否则你回答的语言需要和用户提问的语言保持一致。
# 当前用户信息为：
- 工号：{{#sys.user_id#}}
# 用户消息为：
{{#sys.query#}}

### 构建TT卡片消息.md

```Json
{
	"$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
	"type": "AdaptiveCard",
	"body": [
		{
			"_tag": "promptText",
			"type": "Container",
			"spacing": "default",
			"isVisible": "${promptStatus}",
			"items": [
				{
					"_tag": "title",
					"spacing": "Medium",
					"type": "TextBlock",
					"text": "${prompt}",
					"wrap": true
				}
			]
		},
		{
			"type": "Container",
			"isVisible": "${!promptStatus}",
			"spacing": "default",
			"items": [
				{
					"_tag": "title",
					"spacing": "Medium",
					"type": "TextBlock",
					"size": "Medium",
					"weight": "Bolder",
					"maxLines": 1,
					"text": "${leaveTitle}",
					"wrap": true
				},
				{
					"_tag": "leaveType",
					"spacing": "Medium",
					"type": "ColumnSet",
					"columns": [
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"width": "100px",
							"items": [
								{
									"type": "TextBlock",
									"text": "**??°à????**"
								}
							]
						},
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"items": [
								{
									"type": "TextBlock",
									"text": "${dateDay}"
								}
							]
						}
					]
				},
		        {
					"_tag": "leaveType",
					"spacing": "Medium",
					"type": "ColumnSet",
					"columns": [
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"width": "100px",
							"items": [
								{
									"type": "TextBlock",
									"text": "**?????±??**"
								}
							]
						},
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"items": [
								{
									"type": "TextBlock",
									"text": "${startDttm}"
								}
							]
						}
					]
				},
		        {
					"_tag": "leaveType",
					"spacing": "Medium",
					"type": "ColumnSet",
					"columns": [
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"width": "100px",
							"items": [
								{
									"type": "TextBlock",
									"text": "**?á???±??**"
								}
							]
						},
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"items": [
								{
									"type": "TextBlock",
									"text": "${endDttm}"
								}
							]
						}
					]
				},
		        {
					"_tag": "leaveType",
					"spacing": "Medium",
					"type": "ColumnSet",
					"columns": [
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"width": "100px",
							"items": [
								{
									"type": "TextBlock",
									"text": "**??°à?±?¤**"
								}
							]
						},
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"items": [
								{
									"type": "TextBlock",
									"text": "${durationDays}"
								}
							]
						}
					]
				},
		        {
					"_tag": "leaveType",
					"spacing": "Medium",
					"type": "ColumnSet",
					"columns": [
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"width": "100px",
							"items": [
								{
									"type": "TextBlock",
									"text": "**?á????**"
								}
							]
						},
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"items": [
								{
									"type": "TextBlock",
									"text": "${enddttmModeDesc}"
								}
							]
						}
					]
				},
                 {
					"_tag": "leaveType",
					"spacing": "Medium",
					"type": "ColumnSet",
					"columns": [
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"width": "50px",
							"items": [
								{
									"type": "TextBlock",
									"text": "**???ò**"
								}
							]
						},
						{
							"type": "Column",
							"verticalContentAlignment": "Center",
							"items": [
								{
									"type": "TextBlock",
									"text": "${reason}"
								}
							]
						}
					]
				},
				{
					"_tag": "text",
					"type": "TextBlock",
					"maxLines": 2,
					"text": "${leaveDesc}",
					"wrap": true
				}
			]
		},
		{
			"type": "ActionSet",
			"spacing": "Medium",
			"isVisible": "${btnStatus}",
			"actions": [
				{
					"style": "positive",
					"type": "Action.OpenUrl",
					"title": "${btnTitle}",
					"url": "${btnUrl}"
				}
			]
		},
		{
			"_tag": "feet",
			"isVisible": "${!promptStatus}",
			"type": "TextBlock",
			"color": "light",
			"maxLines": 2,
			"text": "???????????·????±????????????÷°????°?ù?·????",
			"wrap": true
		}
	],
	"version": "1.4"
}
```

### 获取知识库2.md

# 以下内容是基于用户发送的消息的搜索结果:
在我给你的搜索结果中，每个结果都是[file X begin]...[file X end]格式的，X代表每篇文章的数字索引。如果引用的来源文件有查看链接，请在答案末尾按每篇文件的数字索引顺序列出所有引用文件的查看链接<a needAuth /target="_blank" href="PC端链接地址" mobile_url="移动端链接地址">文件名称<\a>。
在回答时，请注意以下几点：
- 并非搜索结果的所有内容都与用户的问题密切相关，你需要结合问题，对搜索结果进行甄别、筛选。
- 对于列举类的问题（如列举所有航班信息），尽量将答案控制在10个要点以内，并告诉用户可以查看搜索来源、获得完整信息。优先提供信息完整、最相关的列举项；如非必要，不要主动告诉用户搜索结果未提供的内容。
- 对于创作类的问题（如写论文），你需要解读并概括用户的题目要求，选择合适的格式，充分利用搜索结果并抽取重要信息，生成符合用户要求、极具思想深度、富有创造力与专业性的答案。你的创作篇幅需要尽可能延长，对于每一个要点的论述要推测用户的意图，给出尽可能多角度的回答要点，且务必信息量大、论述详尽。
- 如果回答很长，请尽量结构化、分段落总结。如果需要分点作答，尽量控制在5个点以内，并合并相关的内容。
- 对于客观类的问答，如果问题的答案非常简短，可以适当补充一到两句相关信息，以丰富内容。
- 你需要根据用户要求和回答内容选择合适、美观的回答格式，确保可读性强。
- 你的回答应该综合多个相关网页来回答，不能重复引用一个网页。
- 除非用户要求，否则你回答的语言需要和用户提问的语言保持一致。
# 当前用户信息为：
- 工号：{{#sys.user_id#}}
# 用户消息为：
{{#sys.query#}}

---

*本文档最后更新时间：2026年2月6日*