// routes/auth.js 包含注册登录 修改密码用户名邮箱 验证token接口
const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const dbModule = require('../db');
const db = dbModule.promisePool;
const { authenticateToken } = require('../middleware/auth');
const verificationService = require('../services/verificationService');
const { writeSystemLog } = require('../utils/systemLog');

// 密码校验：仅字母和数字，长度 1-8 位
function validatePassword(pwd) {
  if (!pwd) return { valid: false, message: '密码不能为空' };
  if (!/^[a-zA-Z0-9]{1,8}$/.test(pwd)) {
    return { valid: false, message: '密码只能包含字母和数字，长度 1-8 位' };
  }
  return { valid: true, message: '' };
}
//管理员登录接口
router.post('/admin/login', async (req, res) => {
  try {
    const { adminAccount, password } = req.body;

    // 验证输入
    if (!adminAccount || !password) {
      return res.status(400).json({
        success: false,
        message: '请填写管理员账号和密码'
      });
    }

    // 从数据库查找管理员用户（role='admin'）
    const [users] = await db.query(
      'SELECT * FROM users WHERE (username = ?) AND role = "admin"', 
      [adminAccount]
    );
    
    if (users.length === 0) {
      await writeSystemLog('warning', `[AUTH] 管理员登录失败-账号不存在或权限不足: ${adminAccount}`);
      return res.status(400).json({
        success: false,
        message: '管理员账号不存在或权限不足'
      });
    }

    const user = users[0];

    // 验证密码
    const isValidPassword = await bcrypt.compare(password, user.password);
    if (!isValidPassword) {
      await writeSystemLog('warning', `[AUTH] 管理员登录失败-密码错误: ${adminAccount}`);
      return res.status(400).json({
        success: false,
        message: '密码错误'
      });
    }

    // 生成JWT token（可以设置不同的密钥或更长的有效期）
    const token = jwt.sign(
      { 
        userId: user.user_id, 
        email: user.email,
        role: user.role,  // 添加角色信息
        isAdmin: true     // 添加管理员标识
      },
      process.env.JWT_SECRET || 'your-secret-key',
      { expiresIn: '7d' }  // 管理员token有效期更长
    );
    
    // 添加系统日志 - 管理员登录成功
    await writeSystemLog('normal', `管理员登录成功: ${adminAccount} (管理员ID: ${user.user_id})`);

    res.json({
      success: true,
      message: '管理员登录成功',
      user: {
        user_id: user.user_id,
        username: user.username,
        email: user.email,
        role: user.role
      },
      token
    });

  } catch (error) {
    console.error('管理员登录错误:', error);
    await writeSystemLog('error', `[AUTH] 管理员登录系统异常: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器错误'
    });
  }
});

// 用户注册接口
router.post('/register', async (req, res) => {
  try {
    const { username, email, password, verificationCode } = req.body;

    // 验证输入
    //删除role的验证
    if ( !username || !email || !password || !verificationCode) {
      return res.status(400).json({
        success: false,
        message: '请填写所有字段'
      });
    }

    // 校验密码格式（仅字母和数字，长度 1-8 位）
    const pwdCheck = validatePassword(password);
    if (!pwdCheck.valid) {
      await writeSystemLog('warning', `[AUTH] 注册失败-密码格式错误: ${email}`);
      return res.status(400).json({
        success: false,
        message: pwdCheck.message
      });
    }

    //检查用户名是否重复
    const [existingUser] = await db.query('SELECT user_id FROM users WHERE username = ?', [username]);
    if (existingUser.length > 0) {
      await writeSystemLog('warning', `[AUTH] 注册失败-用户名已被注册: ${username}`);
      return res.status(400).json({
        success: false,
        message: '该用户名已被注册'
      });
    }
    
    // 验证邮箱验证码
    const isValidCode = await verificationService.verifyCode(email, verificationCode);
    if (!isValidCode) {
      return res.status(400).json({
        success: false,
        message: '验证码错误或已过期'
      });
    }

    // 检查邮箱是否重复
    const [existingUsers] = await db.query('SELECT user_id FROM users WHERE email = ?', [email]);
    if (existingUsers.length > 0) {
      await writeSystemLog('warning', `[AUTH] 注册失败-邮箱已被注册: ${email}`);
      return res.status(400).json({
        success: false,
        message: '该邮箱已被注册'
      });
    }

    // 加密密码
    const hashedPassword = await bcrypt.hash(password, 10);

    // 插入用户
    const [insertResult] = await db.query(
      'INSERT INTO users (username, email, password) VALUES (?, ?, ?)',
      [username, email, hashedPassword]
    );
    
    const userId = insertResult.insertId;

    // 删除已使用的验证码
    await verificationService.deleteCode(email);

    // 生成JWT token
    const token = jwt.sign(
      { userId: userId, email: email },
      process.env.JWT_SECRET || 'your-secret-key',
      { expiresIn: '24h' }
    );

    // 添加系统日志 - 用户注册并登录成功
    await writeSystemLog('normal', `用户注册并登录成功: ${email} (用户ID: ${userId})`);


    res.json({
      success: true,
      message: '注册成功',
      user: {
        user_id: userId,
        username: username,
        email: email,
      },
      token
    });

  } catch (error) {
    console.error('注册错误:', error);
    await writeSystemLog('error', `[AUTH] 注册系统异常: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器错误'
    });
  }
});

// 用户登录接口
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;

    // 验证输入
    if (!email || !password) {
      return res.status(400).json({
        success: false,
        message: '请填写邮箱和密码'
      });
    }

    // 从数据库查找用户
    const [users] = await db.query('SELECT * FROM users WHERE email = ?', [email]);
    
    if (users.length === 0) {
      await writeSystemLog('warning', `[AUTH] 用户登录失败-账号不存在: ${email}`);
      return res.status(400).json({
        success: false,
        message: '用户不存在'
      });
    }

    const user = users[0];

    // 验证密码
    const isValidPassword = await bcrypt.compare(password, user.password);
    if (!isValidPassword) {
      await writeSystemLog('warning', `[AUTH] 用户登录失败-密码错误: ${email}`);
      return res.status(400).json({
        success: false,
        message: '密码错误'
      });
    }

    // 生成JWT token
    const token = jwt.sign(
      { userId: user.user_id, email: user.email },
      process.env.JWT_SECRET || 'your-secret-key',
      { expiresIn: '24h' }
    );
    // 添加系统日志 - 用户登录成功
    await writeSystemLog('normal', `用户登录成功: ${user.email} (用户ID: ${user.user_id})`);

    res.json({
      success: true,
      message: '登录成功',
      user: {
        user_id: user.user_id,
        username: user.username,
        email: user.email
      },
      token
    });

  } catch (error) {
    console.error('登录错误:', error);
    await writeSystemLog('error', `[AUTH] 用户登录系统异常: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器错误'
    });
  }
});

// 修改密码接口
router.post('/change-password', authenticateToken, async (req, res) => {
  try {
    const { currentPassword, newPassword } = req.body;

    // 验证输入
    if (!currentPassword || !newPassword) {
      return res.status(400).json({
        success: false,
        message: '请填写当前密码和新密码'
      });
    }

    // 校验新密码格式（仅字母和数字，长度 1-8 位）
    const pwdCheck = validatePassword(newPassword);
    if (!pwdCheck.valid) {
      return res.status(400).json({
        success: false,
        message: pwdCheck.message
      });
    }

    // 从数据库获取当前用户的完整信息
    const [users] = await db.query('SELECT * FROM users WHERE user_id = ?', [req.user.user_id]);
    
    if (users.length === 0) {
      return res.status(404).json({
        success: false,
        message: '用户不存在'
      });
    }

    const user = users[0];

    // 验证当前密码
    const isCurrentPasswordValid = await bcrypt.compare(currentPassword, user.password);
    if (!isCurrentPasswordValid) {
      return res.status(400).json({
        success: false,
        message: '当前密码错误'
      });
    }

    // 检查新密码是否与旧密码相同
    const isSamePassword = await bcrypt.compare(newPassword, user.password);
    if (isSamePassword) {
      return res.status(400).json({
        success: false,
        message: '新密码不能与当前密码相同'
      });
    }

    // 加密新密码
    const hashedNewPassword = await bcrypt.hash(newPassword, 10);

    // 更新密码
    await db.query(
      'UPDATE users SET password = ? WHERE user_id = ?',
      [hashedNewPassword, req.user.user_id]
    );

    console.log(`用户 ${req.user.user_id} 密码修改成功`);

    res.json({
      success: true,
      message: '密码修改成功'
    });

  } catch (error) {
    console.error('修改密码错误:', error);
    await writeSystemLog('error', `[AUTH] 修改密码系统异常: ${error.message}`);
    res.status(500).json({
      success: false,
      message: '服务器错误，请稍后重试'
    });
  }
});

// 修改用户名接口
router.post('/change-username', authenticateToken, async (req, res) => {
  try {
    const { newUsername } = req.body;

    // 验证输入
    if (!newUsername || newUsername.trim() === '') {
      return res.status(400).json({
        success: false,
        message: '请输入新用户名'
      });
    }

    // 去除空格并验证长度
    const trimmedUsername = newUsername.trim();
    if (trimmedUsername.length > 10) {
      return res.status(400).json({
        success: false,
        message: '用户名最长为10个字符'
      });
    }

    // 检查用户名格式（字母、数字、下划线、中文）
    const usernameRegex = /^[a-zA-Z0-9_\u4e00-\u9fa5]{1,10}$/;
    if (!usernameRegex.test(trimmedUsername)) {
      return res.status(400).json({
        success: false,
        message: '用户名只能包含字母、数字、下划线和中文字符'
      });
    }

    // 从数据库获取当前用户的完整信息
    const [users] = await db.query('SELECT * FROM users WHERE user_id = ?', [req.user.user_id]);
    
    if (users.length === 0) {
      return res.status(404).json({
        success: false,
        message: '用户不存在'
      });
    }

    const user = users[0];

    // 检查新用户名是否与当前用户名相同
    if (user.username === trimmedUsername) {
      return res.status(400).json({
        success: false,
        message: '新用户名不能与当前用户名相同'
      });
    }

    // 检查用户名是否已被其他用户使用[1](@ref)
    const [existingUsers] = await db.query(
      'SELECT user_id FROM users WHERE username = ? AND user_id != ?', 
      [trimmedUsername, req.user.user_id]
    );
    
    if (existingUsers.length > 0) {
      return res.status(400).json({
        success: false,
        message: '该用户名已被其他用户使用，请换一个试试'
      });
    }

    // 更新用户名[2,5](@ref)
    await db.query(
      'UPDATE users SET username = ? WHERE user_id = ?',
      [trimmedUsername, req.user.user_id]
    );

    console.log(`用户 ${req.user.user_id} 用户名修改成功: ${user.username} -> ${trimmedUsername}`);

    res.json({
      success: true,
      message: '用户名修改成功',
      data: {
        oldUsername: user.username,
        newUsername: trimmedUsername
      }
    });

  } catch (error) {
    console.error('修改用户名错误:', error);
    await writeSystemLog('error', `[AUTH] 修改用户名系统异常: ${error.message}`);
    
    // 处理数据库唯一约束错误（备用检查）
    if (error.code === 'ER_DUP_ENTRY' || error.errno === 1062) {
      return res.status(400).json({
        success: false,
        message: '该用户名已被其他用户使用，请换一个试试'
      });
    }
    
    res.status(500).json({
      success: false,
      message: '服务器错误，请稍后重试'
    });
  }
});

// 修改邮箱接口
router.post('/change-email', authenticateToken, async (req, res) => {

  try {
    const { newEmail, verificationCode } = req.body;//删除password

    // 验证输入
    if (!newEmail || !verificationCode) {
      return res.status(400).json({
        success: false,
        message: '请填写新邮箱、验证码和当前密码'
      });
    }

    // 验证邮箱格式
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(newEmail)) {
      return res.status(400).json({
        success: false,
        message: '邮箱格式不正确'
      });
    }

    // 从数据库获取当前用户的完整信息
    const [users] = await db.query('SELECT * FROM users WHERE user_id = ?', [req.user.user_id]);
    
    if (users.length === 0) {
      return res.status(404).json({
        success: false,
        message: '用户不存在'
      });
    }

    const user = users[0];

    // 检查新邮箱是否与当前邮箱相同
    if (user.email === newEmail) {
      return res.status(400).json({
        success: false,
        message: '新邮箱不能与当前邮箱相同'
      });
    }

    // 验证邮箱验证码[1,2](@ref)
    const isValidCode = await verificationService.verifyCode(newEmail, verificationCode);
    if (!isValidCode) {
      return res.status(400).json({
        success: false,
        message: '验证码错误或已过期'
      });
    }

    // 检查新邮箱是否已被其他用户使用
    const [existingUsers] = await db.query(
      'SELECT user_id FROM users WHERE email = ? AND user_id != ?', 
      [newEmail, req.user.user_id]
    );
    
    if (existingUsers.length > 0) {
      return res.status(400).json({
        success: false,
        message: '该邮箱已被其他用户使用'
      });
    }

    // 更新邮箱[1,7](@ref)
    await db.query(
      'UPDATE users SET email = ? WHERE user_id = ?',
      [newEmail, req.user.user_id]
    );

    // 删除已使用的验证码
    await verificationService.deleteCode(newEmail);

    console.log(`用户 ${req.user.user_id} 邮箱修改成功: ${user.email} -> ${newEmail}`);

    res.json({
      success: true,
      message: '邮箱修改成功',
      data: {
        oldEmail: user.email,
        newEmail: newEmail
      }
    });

  } catch (error) {
    console.error('修改邮箱错误:', error);
    await writeSystemLog('error', `[AUTH] 修改邮箱系统异常: ${error.message}`);
    
    // 处理数据库唯一约束错误
    if (error.code === 'ER_DUP_ENTRY' || error.errno === 1062) {
      return res.status(400).json({
        success: false,
        message: '该邮箱已被其他用户使用'
      });
    }
    
    res.status(500).json({
      success: false,
      message: '服务器错误，请稍后重试'
    });
  }
});

// 验证token接口
router.get('/validate', authenticateToken, async (req, res) => {
  res.json({
    success: true,
    user: {
      user_id: req.user.user_id,
      username: req.user.username,
      email: req.user.email
    }
  });
});

module.exports = router;