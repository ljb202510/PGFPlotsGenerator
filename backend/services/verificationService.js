// services/verificationService.js
const nodemailer = require('nodemailer');
const dbModule = require('../db');
const db = dbModule.promisePool;

class VerificationService {
  constructor() {
    this.transporter = null;
    this.initTransporter();
  }

  // 初始化邮件传输器 - 修复函数名
initTransporter() {
  try {
    this.transporter = nodemailer.createTransport({
      host: process.env.SMTP_HOST,        // smtp.qq.com
      port: parseInt(process.env.SMTP_PORT), // 465 转为数字
      secure: true,  // 465端口必须为true
      auth: {
        user: process.env.SMTP_USER,     // 您的QQ邮箱
        pass: process.env.SMTP_PASS      // 新的授权码
      }
    });
    
    // 添加更详细的错误处理
    this.transporter.verify((error, success) => {
      if (error) {
        console.log('邮件服务配置错误详情:', error);
      } else {
        console.log('邮件服务已就绪，可以发送邮件');
      }
    });
  } catch (error) {
    console.error('初始化邮件服务错误:', error);
  }
}

  // 生成6位随机验证码
  generateVerificationCode() {
    const digits = '0123456789';
    let code = '';
    for (let i = 0; i < 6; i++) {
      code += digits[Math.floor(Math.random() * digits.length)];
    }
    return code;
  }

  // 发送注册验证码邮件
  async sendRegisterVerificationEmail(email, code) {
    try {
      const subject = '账号注册验证码';
      const text = `您的注册验证码是：${code}，该验证码10分钟内有效。`;

      const mailOptions = {
        from: process.env.SMTP_FROM,
        to: email,
        subject: subject,
        text: text,
        html: this.generateEmailTemplate(subject, text, code)
      };

      await this.transporter.sendMail(mailOptions);
      return true;
    } catch (error) {
      console.error('发送邮件错误:', error);
      throw error;
    }
  }

  // 生成邮件HTML模板
  generateEmailTemplate(subject, text, code) {
    return `
    <!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>注册验证码</title>
    <style type="text/css">
        @media screen and (max-width: 600px) {
            .container {
                width: 100% !important;
            }
            .content-cell {
                padding: 20px 15px !important;
            }
            .code-box {
                padding: 15px !important;
            }
            .header-title {
                font-size: 24px !important;
            }
        }
    </style>
</head>
<body style="margin: 0; padding: 0; background-color: #f5f5f5; font-family: Arial, 'Microsoft YaHei', sans-serif;">
    <!-- 外层容器 -->
    <table width="100%" border="0" cellspacing="0" cellpadding="0" bgcolor="#f5f5f5">
        <tr>
            <td align="center" style="padding: 20px 0;">
                <!-- 主内容表格，宽度限定为600px以确保兼容性[1](@ref) -->
                <table class="container" width="600" border="0" cellspacing="0" cellpadding="0" bgcolor="#ffffff" style="border-collapse: collapse; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                    <!-- 页眉部分 -->
                    <tr>
                        <td class="content-cell" bgcolor="#667eea" style="padding: 40px 30px; text-align: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
                            <h1 class="header-title" style="margin: 0; font-size: 28px; color: white; font-weight: bold;">注册验证码</h1>
                        </td>
                    </tr>
                    
                    <!-- 内容区域 -->
                    <tr>
                        <td class="content-cell" style="padding: 40px 30px; font-size: 16px; line-height: 1.6; color: #333;">
                            <h2 style="color: #333; margin-top: 0; margin-bottom: 20px;">${subject}</h2>
                            
                            <p style="margin-bottom: 15px;">尊敬的用户，您好！</p>
                            <p style="margin-bottom: 30px;">${text}</p>
                            
                            <!-- 验证码展示框 -->
                            <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                <tr>
                                    <td align="center">
                                        <div class="code-box" style="background: white; padding: 25px; margin: 25px 0; text-align: center; border-radius: 8px; border: 2px dashed #1890ff;">
                                            <span style="font-size: 36px; font-weight: bold; color: #1890ff; letter-spacing: 8px; line-height: 1.2;">${code}</span>
                                        </div>
                                    </td>
                                </tr>
                            </table>
                            
                            <!-- 安全提示 -->
                            <table width="100%" border="0" cellspacing="0" cellpadding="0" bgcolor="#fff8e1" style="border-left: 4px solid #ffd54f;">
                                <tr>
                                    <td style="padding: 15px;">
                                        <p style="color: #e65100; font-size: 14px; line-height: 1.5; margin: 0;">
                                            <strong>重要提示：</strong>请勿将验证码泄露给他人。此验证码10分钟内有效，如非本人操作，请立即忽略此邮件。
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    
                    <!-- 页脚 -->
                    <tr>
                        <td bgcolor="#f5f5f5" style="padding: 20px; text-align: center; color: #999; font-size: 12px;">
                            <p style="margin: 0 0 5px 0;">此邮件由系统自动发送，请勿回复。</p>
                            <p style="margin: 0;">如果您有任何疑问，请联系客服</p>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>
    `;
  }

  // 存储验证码到数据库
  async storeVerificationCode(email, code) {
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10分钟后过期
    
    // 删除该邮箱之前的验证码
    await db.query('DELETE FROM email_verification_codes WHERE email = ?', [email]);
    
    // 插入新验证码
    await db.query(
      'INSERT INTO email_verification_codes (email, code, expires_at) VALUES (?, ?, ?)',
      [email, code, expiresAt]
    );
  }

  // 验证验证码
  async verifyCode(email, code) {
    const [codes] = await db.query(
      `SELECT * FROM email_verification_codes 
       WHERE email = ? AND code = ? AND expires_at > NOW()`,
      [email, code]
    );

    return codes.length > 0;
  }

  // 删除验证码
  async deleteCode(email) {
    await db.query('DELETE FROM email_verification_codes WHERE email = ?', [email]);
  }

  // 发送注册验证码（主方法）
  async sendRegisterCode(email) {
    try {
      // 验证邮箱格式
      if (!this.isValidEmail(email)) {
        throw new Error('邮箱格式不正确');
      }

      // 检查邮箱是否已被注册
      const [existingUsers] = await db.query('SELECT user_id FROM users WHERE email = ?', [email]);
      if (existingUsers.length > 0) {
        throw new Error('该邮箱已被注册');
      }

      // 生成验证码
      const verificationCode = this.generateVerificationCode();

      // 存储验证码
      await this.storeVerificationCode(email, verificationCode);

      // 发送邮件
      await this.sendRegisterVerificationEmail(email, verificationCode);

      console.log(`注册验证码发送成功：${email}`);

      return {
        success: true,
        email: email,
        expires_in: 600 // 10分钟
      };

    } catch (error) {
      console.error('发送验证码错误:', error);
      throw error;
    }
  }

  // 验证邮箱格式
  isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }
}

module.exports = new VerificationService();