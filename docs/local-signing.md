# 本地版本签名

本地交付的 `com.hongguo.android` 使用固定证书，SHA-256 为：

`2eb75370e7dee27afc7297eb7c3ef2ddbef0e37144780414825b5f11421dcb13`

默认使用构建机器已有的 `~/.android/debug.keystore`。需要指定同一密钥的其他位置时，可传入 `-PlocalSigningStore=/绝对路径`。构建前会验证证书，密钥缺失或签名不一致时停止；debug 和 release 均使用该签名配置。

密钥须安全保管并另行备份；证书指纹不能代替私钥。不要将密钥或密码提交到仓库。丢失原密钥后不能无损覆盖升级，应先找回密钥。

上游 `release-signing.sha256` 和 `scripts/prepare-release.ps1` 属于上游发布配置，其签名与本地版本不同，不能用于发布本地版本。上游更新清单也不能自动变成本地同签名更新；在线更新需另行配置受控发布源。
