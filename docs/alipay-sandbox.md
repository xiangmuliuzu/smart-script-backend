# 支付宝沙箱支付配置

本实现只连接支付宝官方沙箱网关，不支持真实收款或提现。默认只允许名称以 `_sandbox` 结尾的数据库。用于尚未上线的开发演示库时，必须通过 `APP_ALIPAY_SANDBOX_ALLOWED_DATABASES` 精确列出库名，例如 `script_platform_dev`；不要授权正式业务库。

1. 在支付宝开放平台创建沙箱应用，配置 RSA2 应用公钥，并获取沙箱 AppID、应用私钥和支付宝沙箱公钥。将密钥保存在测试环境的私有配置中，不要提交到代码仓库。
2. 独立沙箱库需以 `_sandbox` 结尾，并先执行 `sql/sandbox_payment_support.sql`、再执行支付迁移。未上线的开发演示库已有业务表时，只执行 `sql/migrations/c/C_20261030_001__alipay_sandbox_payment.sql`；不要在正式业务库执行。
3. 在开发演示服务配置 `APP_ALIPAY_SANDBOX_ENABLED=true`、`APP_ALIPAY_SANDBOX_ALLOWED_DATABASES=script_platform_dev`、`APP_ALIPAY_SANDBOX_APP_ID`、`APP_ALIPAY_SANDBOX_PRIVATE_KEY` 和 `APP_ALIPAY_SANDBOX_PUBLIC_KEY`。私钥和公钥使用支付宝 SDK 公钥模式的 Base64 内容；支付网关固定为支付宝沙箱，不可切换到正式网关。缺少任一密钥或当前库未被精确授权时，支付入口拒绝执行。
4. APP 和企业端使用开发演示服务。合同双签后点击付款，使用支付宝沙箱钱包扫描二维码；支付宝会通知开发服务，用户也可手动查询支付结果。设置 `APP_ALIPAY_SANDBOX_NOTIFY_URL` 为开发服务的 HTTPS 通知地址以启用自动通知。若交易已关闭，查询后可重新发起支付。
5. 服务端通过支付宝 SDK 发起预创建并查询交易；仅在支付宝确认成功、商户订单号与金额匹配时推进订单。订单状态及后续结算均为测试数据。接口重复确认按支付记录幂等处理。

支付宝官方沙箱接入说明：https://open.alipay.com/paymentServicer/paymentProvider.htm
