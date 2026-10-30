# 支付宝沙箱支付配置

本实现只供独立测试环境使用，不支持真实收款或提现。不要在正在使用的业务数据库运行沙箱订单、交割或结算流程，也不要将沙箱数据库连接到正式服务。

1. 在支付宝开放平台创建沙箱应用，配置 RSA2 应用公钥，并获取沙箱 AppID、应用私钥和支付宝沙箱公钥。将密钥保存在测试环境的私有配置中，不要提交到代码仓库。
2. 新建独立测试数据库，名称必须以 `_sandbox` 结尾，例如 `smartscript_sandbox`。按项目初始化流程准备测试库，再依次对测试库执行 `sql/sandbox_payment_support.sql` 和 `sql/migrations/c/C_20261030_001__alipay_sandbox_payment.sql`；不要把正式用户、订单、结算资料导入测试库。
3. 在仅连接该测试库的后端实例中配置 `APP_ALIPAY_SANDBOX_ENABLED=true`、`APP_ALIPAY_SANDBOX_APP_ID`、`APP_ALIPAY_SANDBOX_PRIVATE_KEY` 和 `APP_ALIPAY_SANDBOX_PUBLIC_KEY`。私钥和公钥使用支付宝 SDK 公钥模式的 Base64 内容；支付网关固定为支付宝沙箱，不可切换到正式网关。缺少任一配置或数据库名称不符合要求时，支付入口拒绝执行。
4. APP 和企业端的测试账号需要使用测试实例。合同双签后点击付款，使用支付宝沙箱钱包扫描二维码，再点击“查询支付结果”。APP 上的二维码可用另一台安装沙箱钱包的设备扫描。设置 `APP_ALIPAY_SANDBOX_NOTIFY_URL` 为沙箱服务的 HTTPS 通知地址后，可由支付宝自动通知；用户仍可手动查询支付结果。未设置通知地址时需要手动查询。若交易已关闭，查询后可重新发起支付。
5. 服务端通过支付宝 SDK 发起预创建并查询交易；仅在支付宝确认成功、商户订单号与金额匹配时推进订单。订单状态及后续结算均为测试数据。接口重复确认按支付记录幂等处理。

支付宝官方沙箱接入说明：https://open.alipay.com/paymentServicer/paymentProvider.htm
