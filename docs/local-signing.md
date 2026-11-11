# 本地版本签名

本地交付的 `com.hongguo.android` 使用固定证书，SHA-256 为：

`2eb75370e7dee27afc7297eb7c3ef2ddbef0e37144780414825b5f11421dcb13`

本机默认使用 `~/.android/debug.keystore`，也可用 `-PlocalSigningStore=/绝对路径` 指定同一密钥。构建前校验证书；GitHub Actions 无密钥编译测试，在独立步骤使用仓库 Secrets 签名并核对证书。

密钥须安全保管并另行备份；证书指纹不能代替私钥。不要将密钥或密码提交到仓库。丢失原密钥后不能无损覆盖升级，应先找回密钥。

上游 `release-signing.sha256` 和 `scripts/prepare-release.ps1` 属于上游发布配置，不用于本仓库发布。本仓库的手机更新清单与 APK 均由 `.github/workflows/release.yml` 生成。
