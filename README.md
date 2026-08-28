# AICR API Gateway

将小米MIUI AICR (AI Cognition/Recognition) 端侧AI引擎的能力封装为HTTP REST API，供其他程序或脚本调用。

## 功能特性

- **OCR文字识别** - 识别图片中的文字，返回文字内容和位置坐标
- **NER实体提取** - 从文本中提取地址、电话、身份证号等实体
- **中文分词** - 中文文本分词
- **图像分割** - 人像/天空/物体分割
- **API Key认证** - 可选的认证机制
- **配置界面** - Android配置界面，方便管理

## 系统要求

- 已root的Android设备
- LSPosed框架 (Android 12+)
- MIUI AICR应用 (com.xiaomi.aicr)

## 安装

1. 从 [Releases](../../releases) 下载最新APK
2. 通过LSPosed安装模块
3. 在LSPosed中启用模块，勾选"系统框架"和"com.xiaomi.aicr"
4. 重启设备
5. 打开AICR API Gateway应用进行配置

## API接口

### 基础信息

- **Base URL**: `http://<device-ip>:8080/api/v1`
- **Content-Type**: `application/json` 或 `multipart/form-data`
- **认证**: 可选 `X-API-Key` Header

### 1. OCR文字识别

```bash
curl -X POST http://192.168.1.100:8080/api/v1/ocr \
  -F "file=@image.png"
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "status": 0,
    "texts": [
      {
        "content": "识别的文字",
        "boundingBox": [x1, y1, x2, y2, x3, y3, x4, y4]
      }
    ]
  }
}
```

### 2. NER实体提取

```bash
curl -X POST http://192.168.1.100:8080/api/v1/ner \
  -H "Content-Type: application/json" \
  -d '{"text": "张三的电话是13800138000，地址是北京市朝阳区"}'
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "statusCode": 0,
    "entities": [
      {
        "str": "北京市朝阳区",
        "type": 1,
        "typeName": "LOCATION",
        "start": 20,
        "end": 26
      },
      {
        "str": "13800138000",
        "type": 3,
        "typeName": "TEL",
        "start": 6,
        "end": 17
      }
    ]
  }
}
```

**实体类型**:
| type | 名称 | 说明 |
|------|------|------|
| 0 | UNKNOW | 未知 |
| 1 | LOCATION | 地址 |
| 2 | ID | 身份证号 |
| 3 | TEL | 电话号码 |
| 4 | BCN | 银行卡号 |
| 5 | TRAIN | 火车票号 |
| 6 | FLN | 航班号 |
| 7 | CAR | 车牌号 |
| 8 | ORDER | 订单号 |
| 9 | ENO | 企业编号 |

### 3. 中文分词

```bash
curl -X POST http://192.168.1.100:8080/api/v1/ner/tokenize \
  -H "Content-Type: application/json" \
  -d '{"text": "小米公司成立于2010年"}'
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "tokens": ["小米", "公司", "成立", "于", "2010", "年"]
  }
}
```

### 4. 图像分割

```bash
curl -X POST http://192.168.1.100:8080/api/v1/segment \
  -F "file=@photo.jpg" \
  -F "type=person"
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "segments": [
      {
        "type": "person",
        "mask": "base64...",
        "width": 1920,
        "height": 1080
      }
    ]
  }
}
```

### 5. 服务状态

```bash
curl http://192.168.1.100:8080/api/v1/status
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "service": "AICR API Gateway",
    "version": "1.0.0",
    "aicrConnected": true,
    "capabilities": ["ocr", "ner", "segment"]
  }
}
```

## Python示例

```python
import requests

BASE_URL = "http://192.168.1.100:8080/api/v1"
API_KEY = "your_api_key"  # 如果启用了认证

headers = {"X-API-Key": API_KEY} if API_KEY else {}

# OCR
with open("image.png", "rb") as f:
    response = requests.post(f"{BASE_URL}/ocr", files={"file": f}, headers=headers)
    print(response.json())

# NER
response = requests.post(f"{BASE_URL}/ner", json={"text": "张三的电话是13800138000"}, headers=headers)
print(response.json())

# Image Segmentation
with open("photo.jpg", "rb") as f:
    response = requests.post(f"{BASE_URL}/segment", files={"file": f}, data={"type": "person"}, headers=headers)
    print(response.json())
```

## 配置

打开AICR API Gateway应用，可以配置：

- **启用/禁用服务** - 控制HTTP服务器运行状态
- **端口号** - 默认8080，可自定义
- **API Key认证** - 可选，启用后需要在请求中携带API Key
- **日志** - 控制是否记录请求日志

## 安全建议

- 建议在可信网络中使用
- 启用API Key认证以增加安全性
- 不要在公网暴露此服务

## 技术架构

```
外部客户端 → HTTP → AICR API Gateway (LSPosed模块) → AIDL → AICR系统服务
```

- **LSPosed模块** - 系统级Hook能力
- **NanoHTTPD** - 轻量级HTTP服务器
- **AIDL绑定** - 与AICR服务通信

## 开发

### 构建

```bash
git clone https://github.com/your-username/aicr-api-gateway.git
cd aicr-api-gateway
./gradlew assembleDebug
```

### 项目结构

```
aicr-api-gateway/
├── app/src/main/java/com/aicr/gateway/
│   ├── MainHook.java          # LSPosed入口
│   ├── hook/                  # Hook逻辑
│   ├── server/                # HTTP服务器
│   ├── handler/               # API处理器
│   ├── auth/                  # 认证系统
│   └── util/                  # 工具类
└── .github/workflows/         # CI/CD配置
```

## License

MIT License

## 致谢

- [LSPosed](https://github.com/LSPosed/LSPosed) - Xposed框架
- [NanoHTTPD](https://github.com/NanoHTTPD/nanohttpd) - HTTP服务器
- 小米AICR团队 - 提供端侧AI能力
