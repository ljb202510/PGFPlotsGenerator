// routes/verification.js
const express = require('express');
const router = express.Router();
const verificationService = require('../services/verificationService');

// 发送注册验证码接口
router.post('/send-register-code', async (req, res) => {
  try {
    const { email } = req.body;

    // 基本验证
    if (!email) {
      return res.status(400).json({
        success: false,
        message: '请输入邮箱地址'
      });
    }

    // 发送验证码
    const result = await verificationService.sendRegisterCode(email);

    res.json({
      success: true,
      message: '验证码发送成功',
      data: result
    });

  } catch (error) {
    console.error('发送验证码错误:', error);
    
    let message = '发送验证码失败，请稍后重试';
    let statusCode = 500;

    if (error.message.includes('邮箱格式不正确')) {
      message = '邮箱格式不正确';
      statusCode = 400;
    } else if (error.message.includes('该邮箱已被注册')) {
      message = '该邮箱已被注册';
      statusCode = 400;
    } else if (error.message.includes('发送过于频繁')) {
      message = '发送过于频繁，请稍后再试';
      statusCode = 429;
    } else if (error.message.includes('邮件服务配置错误')) {
      message = '邮件服务暂时不可用，请联系管理员';
      statusCode = 500;
    }

    res.status(statusCode).json({
      success: false,
      message: message
    });
  }
});

// 验证注册验证码接口
router.post('/verify-register-code', async (req, res) => {
  try {
    const { email, code } = req.body;

    if (!email || !code) {
      return res.status(400).json({
        success: false,
        message: '请提供邮箱和验证码'
      });
    }

    // 验证验证码
    const isValid = await verificationService.verifyCode(email, code);

    if (!isValid) {
      return res.status(400).json({
        success: false,
        message: '验证码错误或已过期'
      });
    }

    // 验证成功后删除验证码（一次性使用）
    await verificationService.deleteCode(email);

    res.json({
      success: true,
      message: '验证码验证成功'
    });

  } catch (error) {
    console.error('验证验证码错误:', error);
    res.status(500).json({
      success: false,
      message: '验证失败，请稍后重试'
    });
  }
});

module.exports = router;