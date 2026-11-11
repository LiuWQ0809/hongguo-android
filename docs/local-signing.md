# 本地版本签名

本地交付的 `com.hongguo.android` 使用固定证书，SHA-256 为：

`2eb75370e7dee27afc7297eb7c3ef2ddbef0e37144780414825b5f11421dcb13`

本机默认使用 `~/.android/debug.keystore`，也可用 `-PlocalSigningStore=/绝对路径` 指定同一密钥。构建前校验证书；定时同步、构建、验证和签名均在本机完成。

密钥须安全保管并另行备份；证书指纹不能代替私钥。不要将密钥或密码提交到仓库。丢失原密钥后不能无损覆盖升级，应先找回密钥。

上游 `release-signing.sha256` 和 `scripts/prepare-release.ps1` 属于上游发布配置，不用于本仓库发布。GitHub Actions 工作流已停用，密钥未保存在 GitHub Secrets；公开的 GitHub Releases 用于托管 APK 和更新清单。
