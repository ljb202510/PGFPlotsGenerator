# 开发日志 — PGFPlotsGenerator
> 本日志为历史记录，权威技术文档以 README.md 为准
> 项目：PGFPlotsGenerator（前端 Vue3 + 后端 Spring Boot + MySQL；2026-09-13 前为 Node/Express）
> 记录区间：2025-12-03 ～ 2026-09-16

## 目录
- 一、前期已完成项
- 二、日常开发记录（按时间）
- 三、会议汇报
- 四、老师建议
- 五、待定 / 废除项 / 疑问
- 六、部署相关
- 七、技术说明与参考
- 八、2026-09-13 Java 后端重构与后续（整合：重构 / 配套修复 / Node 退役；分界线以上为 Node.js 版开发记录）

---

## 一、前期已完成项

- `eventBus.emit` 事件总线的方法，修改成功 - 事件监听
- Vuex 状态管理（于 16:30 改好，没有使用 pinia）

---

## 二、日常开发记录（按时间）

### 2025年12月3日

#### 20:01:43
晚上 2h 在修改浏览器的历史记录存储，测试一些 bug：

1. 在密码之前/开头添加了隐藏字段 `input type=password`
2. 每个输入添加 `autocomplete="off"`
3. 密码属性 `autocomplete="new-password"`
4. 尝试密码字段中添加随机 `name` 属性和 `readonly` 属性：

   首先看看浏览器填充表单密码的机制（各浏览器机制有些许不同），以 Chrome 为例，机制如下：
   - 此域名下保存过用户信息。
   - 找到第一个 `input[type=password]` 元素，填充密码；再找到其上一个 `input[type=text]` 元素，填充用户名。

#### 22:35:24
爆肝两个小时  
`/* 添加text的自定义黑圆点CSS样式*/`  
最后这样部分解决问题，直接不要 `type=password`

#### 12月初补充记录
5. 对话界面拖拽上传，会显示"上传失败: {"code":400,"message":"数据集名称和描述不能为空"}"，名称和描述默认为上传的文件名
5. 每次对话之后，应当清除选中文件。
2. 数据上传的"修改"按钮应为编辑按钮（默认显示当前名称和描述，支持修改）
5. 当点击"历史记录"时，后端终端报错：从数据库获取历史记录失败: Error: You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near '?' at line 20
5. 文件大小限制 10000 字节，太小了

---

1. 下载 LaTeX 本地环境，不然无法编译
2. API 调用错误的情况，插入 callid 逻辑还没修改
3. 前后端编译对接问题（历史记录+图表生成界面两个接口）
2. 图表类型的完善？应该有什么类型，删掉吧，类型太多了（数据库和前后端代码都要删除）
`<!-- PDF预览对话框 -->` 预览框的关闭（X 图标），无法关闭整个框（会留下这一行）
1.5 图表生成和历史记录生成的 pdf 应当一致，统一显示
5. API 调用错误: 发送 `.xlsx` 文件，报错，之后都不能调用 AI。
3. 在图表生成界面一旦点击其他组件/页面，就会清除所有记录，我不希望这样，我希望只有退出登录或者刷新界面（再添加一个按钮，"新建对话"按下按钮时）才会清除当前所有对话记录
2. 发出消息如果带有文件，需要在页面上显示出来，而不是只显示文字部分的描述

---

7. 拖拽的区域可以参考 deepseek 而不是固定在右端，底层可以有一个上传历史文件的选项，弹出原来右边"历史文件"的部分，"点击上传"的按钮也需要添加在底部，对话界面可占据全部页面
1. 上传历史文件界面不友好（显示不好看）

### 周二 12.16

- 删除描述中的"用户查询"删除字段
- 删除重新生成选项
3. 上传历史文件里的"取消"和"确认选择"删除这两个按钮相关代码

- AI 回复距离底部输入框有很长的空白区域，去掉；
- 切换回图表生成界面时，默认在最底部而不是最开始的地方
3. 历史记录界面 报错 删除失败: 服务器内部错误，先删除子表（api_log）中的关联记录，再删除主表记录；外键依赖错误。

---

1. 调用 API 具体后端报错需要显示在前端页面上（其实是前后端没有统一报错信息）

---

### 12.16 周二 todolist
- 2025年12月16日 10:36:45 开始
- 2025年12月16日 20:54:07 进度为 1/100 哈哈哈
- 2025年12月16日 23:06:12 不急不急慢慢来

把所有可能的功能跑一遍

### 2025年12月17日

#### 15:16:35
更新了 `compile.js` `history.js` `1.sql` 除了反馈左侧三界面都有更新

#### 19:00:17
已做 3h20min，继续干，now 2025年12月17日 20:36:33

### 周三 12.17

5. enter 发送，shift+enter 换行
3. 删除数据集报错，生成历史引用了这张表，设置为 NULL 即可
1. 当发送代码希望迭代修改时，AI 回复只有代码没有编译选项？提取不出来（要查看前端的处理逻辑）-- 最后是修改了 airesponse 的保存，发现 finalchartcode 没有正确复制到 chartcode 里面
2. 编译错误报错没有显示报错信息。（这个需要实现）
3. 显示时间的具体到分钟，历史记录界面，上传数据界面。统一显示年月日时分
2. 增加提示语，用户友好性，提示：如果需要修改 AI 生成的代码，复制代码到输入框+修改说明即可
3. 用户发送的信息，可以复制
我感觉这个 `APP.vue` 有点冗余了，帮我把顶部导航栏单独分出来一个组件（类似 CommonSidebar 侧边栏组件）
4. 通知功能
现在你是一位计算机专家。
这里是我的顶部导航栏组件 `CommonNavbar.vue`，以及数据库表 `1.sql`，基于此修改
我接下来想开发一个功能：
在顶部导航栏（class=user-name）左侧增加一个通知图标（能显示未读消息数），点击展示消息列表（按键方法与 `goToChangeInformation` 类似，跳转到 `/MyNotice.vue` 组件）。在通知组件调用后端获取通知接口，从数据库 `feedback` 表里面获取内容 `answer` 和时间 `answer_time`；还有数据库 `notice` 表获取信息。这个消息列表会展示我（用户）发送的反馈+如果管理员回复就展示在同一个会话里面，以及管理员发布的通知。
请帮我完成 `CommonNavbar.vue` 的修改，`MyNotice.vue` 的完整代码，后端 `mynotice.js` 的完整代码（暂时使用临时数组，不使用数据库）

### 周四 2025年12月18日

#### 10:08:29 -- 已做 1h

1. 管理员登录功能
你现在是一个计算机专家，这是我的前端登录和后端文件以及数据库。
帮我添加一个管理员登录的选项，并参考登录表单 `loginform.vue` 完成管理员登录表单 `AdminLogin.vue`（两行输入框，一行管理员账号，一行密码），并调用后端 `auth.js` 文件登录接口（新建一个管理员登录接口）完成管理员登录验证，并在 `1.sql` 数据库预置管理员账号密码：admin123 密码：666666。

2. 登录成功后简单的管理员欢迎界面
（样式与 `CommonSidebar.vue` 一致，修改图标和跳转，从上到下分为 通知管理 系统监控 用户管理 处理反馈）

3. 管理员的反馈管理界面（计划跳转新开一个界面）
帮我完成管理员的反馈管理界面，这里有四个文件：反馈后端文件，数据库文件，反馈管理界面（待开发），用户提交反馈的界面。
请你根据以上文件完成管理员的反馈管理界面

---

4. 管理员界面跳转到反馈管理界面 - 改了 2h 还没改好。URL 能改变，但是不能跳转，明明和 `gotochangeInformation` 差不多
根本原因：管理员登录后，`App.vue` 只渲染了固定的 Admin 组件，没有提供路由视图容器。`router-view`

#### 14:28:43
继续改 -- 改到了 17:20 整整三小时，晚上又改了一小时

5. 管理员登录后添加 `<router-view></router-view>` 即可，成功跳转。
修改原来的用户 token 获取方式，改为管理员
希望将反馈管理跳转，新开一个标签页。
但是衍生了很多 bug：

------------------------------------------------------------------------------------------------------------------------------------
现在普通用户和管理员登录成功之后都会看到一闪而过的"登录界面"（包含邮箱 密码输入框，但其他导航栏正常显示），我已经改了很久了还是没解决。请帮我仔细检查。给出几种解决方案

0. 渲染逻辑冲突：App.vue 的模板根据认证状态条件渲染，而路由守卫也进行重定向。如果冲突的话只取其中一种渲染逻辑吧。
1. 删除了路由守卫 `index.js`，但还是不行结果一样
2. 猜想，当时为了解决新开标签页（跳转反馈管理）会弹出"请先登录"的认证问题添加的 `created` 和 `watch`（使用 `checkAuthStatus` 多了，原来只在 `mounted` 使用，现在 `created` 和 `watch` 都使用了）
3. 改了十几次代码（`App.vue` 和 `index.js`）都没解决问题，会不会是其他组件代码有问题，你还需要查看什么文件可以告诉我。

这里把 `index.js` 里面的重定向方法删除，用户登录一切正常。管理员登录很不正常（但也没什么大问题？）

为什么用户登录的时候可以很顺畅的显示 `/chart-generator` 图表生成界面，管理员登录的时候却会把一个奇怪的界面（图表生成界面+管理员顶部导航栏）一闪而过，然后才进入 `Admin.vue` 界面。甚至，如果我没有退出管理员登录的情况下再次访问前端 8080 端口界面，这个奇怪的界面（图表生成界面+管理员顶部导航栏）就会一直显示。而如果我没有退出用户登录的情况下再次访问前端 8080 端口界面，界面会跳转到初始的登录界面。

1. 告诉我原因
2. 给出解决方案
3. 在你的解决方案之后，我想再给出一种方案：效仿用户登录的情况，如果我没有退出管理员登录的情况下再次访问前端 8080 端口界面，界面会跳转到初始的登录界面。

------------------------------------------------------------------------------------------------------------------------------------
如果很多 bug 就还是变成侧边栏吧唉。
目前版本 bug：用户登录一切正常。管理员登录很不正常（但也没什么大问题？偶尔会出现图表生成界面罢了）
------------------------------------------------------------------------------------------------------------------------------------

6. 优化 AI 直接生成的反馈界面，有很多显示不完全的地方
你现在是一位计算机专家。
0. 搜索功能显示歪了一点
1. 搜索功能有没有实现，如果没有，帮助我实现，算了先注释掉

---

#### 周四晚上 19:20-21:20

1. 用户端查看通知，与后端尝试对接
你是一位计算机专家，帮助我使得前端正确调用后端接口（不改变后端文件的前提），使用后端数据库真实数据，删除模拟数据
2. 界面的 `td class="actions-cell"` 三个按钮都无法正常显示，只看得见颜色看不见文字。

7. 反馈管理界面后端没有回复接口，需要添加。对应前端也要修改。（需要把反馈管理界面所有功能测试一遍）

---

#### 21:20:58
全力准备明天小班课内容即可

3. 把要求发给 AI，让它捋一遍小班课要求，讲解，有什么修改的再说。

---

5. 周五（今天）提交两个需求文档，可能可以让 AI 生成？关于 md 格式 doc 格式

### 周五 2025年12月19日

几天的目标：
尽可能数据库就定下了不要 source，因为要测试很多，然后对比

1. 把所有可能的业务流程跑一遍，确保没有问题；
4. 进行代码互查，除谷歌编码规范外，（PM 群腾讯文档）以上问题也是互查重点。让 AI 规范一下
6. 弄多一点数据集让 AI 测试一下啊
7. 十五周之前需要部署服务器

---

1. AI 的提示词优化

0. computed 的使用？？

可能的功能拓展
（用户）AI 的转发收藏功能是否需要实现，查看通知

### 周日 2025年12月21日 23:17:49

（无内容）

### 周一 2025年12月22日 13:58:44

总：添加管理员，处理反馈+发布通知；用户端，查看回答+查看通知

0. 管理员回复反馈后，用户可接收通知，不修改数据库，细节如下：
1. 后端新增一个可以根据用户 ID 获取反馈的接口
2. 在前端完成正确调用后端反馈接口即可。（获取反馈的回复显示在前端，查看回复需要获取---用户自己的反馈）
在我的通知界面，其中反馈通知（如果管理员回复了应该正常接收）
帮我正确完成后端反馈接口的调用，反馈通知不计入未读消息数（没有已读状态未读状态，不用考虑），notice 表是系统通知表和反馈表不一样，在"我的通知"页面，单独从 feedback 表中获取当前用户被回复的反馈，作为反馈通知展示。

---

1. 点击预览 pdf，可以调用 `showPdfPreview` 显示 pdf，
但我不知道怎么获取 `pdf_url`（这个原来是在后端 `compile.js` 调用编译接口构建的路径）
在后端添加 获取 PDF URL 接口 解决。

### 周日 2025年12月28日

#### 18:00:16
开始。

#### 18:27:37
0. 考虑把管理员功能做成侧边栏
具体指令：
保留 `admin.vue` 欢迎横幅和管理员信息这两部分，将快速操作改为侧边栏，样式和路由跳转参考普通用户导航栏 `CommonSidebar.vue`
帮我修改 `App.vue` 主界面，`Admin.vue` 管理员主界面，完成新建 `AdminSidebar.vue` 管理员侧边导航栏组件

1. 管理员通知管理功能
根据描述，生成完整的 vue3 组件代码，`1.sql` 为数据库，里面包含通知表，按照数据库表进行编程（不修改数据库）。`notice.js` 为用户获取通知的接口后端文件，可以参考。生成管理员的通知管理界面代码 `AdminNotice.vue` 以及对应的后端接口（使用 node.js） `AdminNotice.js` 文件（使用临时数据库，保证前后端可以正常对接即可）

2.1. 在这个通知管理界面，我把窗口缩小或者放大一半之后，浏览器弹出警告，怎么解决。
腾讯元宝+deepseek，都不行。使用 gemini3-pro 解决

---

5. 验证每个页面的分页是否有问题
（现在用户管理界面有问题，会直接消失）已解决

4.

5. 完成以上功能的对接

### 周一 2025年12月29日 10:23:00

0. 现在还是有 bug，管理员未退出登录，重新进去的时候默认显示管理员顶部导航栏和侧边导航栏，但是界面却变成 chart-generator 界面（默认显示界面，浏览器顶部 URL 为 `http://localhost:8080/chart-generator`），我应该在哪个地方调整代码

3. 开发管理员的用户管理功能
根据描述，生成完整的 vue3 组件代码，`1.sql` 为数据库，里面包含用户表，按照数据库表进行编程（不修改数据库）。生成管理员的用户管理界面代码 `AdminUser.vue` 以及对应的后端接口（使用 node.js） `AdminUser.js` 文件（使用临时数据库，保证前后端可以正常对接即可），界面包括用户管理的常见功能即可

4. 编辑功能删除，优化前端界面。很多不必要的"对话框"等等
5. 优化通知管理界面，取消已读状态。

6. 开发系统监控功能
根据描述，生成完整的 vue3 组件代码。
`1.sql` 为数据库，里面包含 api 调用表和系统日志表，按照数据库表进行编程（不修改数据库）。生成管理员的系统监控界面代码 `AdminLog.vue` 以及对应的后端接口（使用 node.js） `AdminLog.js` 文件（使用临时数据库，保证前后端可以正常对接即可），界面包括用户管理的常见功能即可

以下为需求说明：

**3.2.10 系统监控**

- **3.2.10.1 特性描述**
  管理员需要监控系统运行状态的时候，可以通过系统监控功能查看系统健康状况。

- **3.2.10.2 刺激响应序列**
  - 刺激：管理员进入后台管理面板，点击系统监控
    响应：系统显示监控仪表盘
  - 刺激：管理员查看服务状态
    响应：系统显示大模型 API 调用成功率、响应时间等指标
  - 刺激：管理员查看实时日志
    响应：系统显示最近的系统错误日志或警告信息

---

总结：
1. 开发通知功能（后端需要对接数据库）
2. 实现未读气泡（等 1 开发完先，决定删除所有有关已读未读状态的显示，否则需要增加一个表用户-通知表，标识每个用户的已读状态）
3. 开发用户管理功能（后端对接数据库）
4. 管理员首页对接数据库

5. 管理员界面在"提交时间" "发布时间" "注册时间" 右边增加箭头，点击可按时间正序/逆序排序。

---

1. "提交时间"的箭头样式需要调整
2. 用户界面 上传数据 "上传时间" 右边增加箭头，点击可按时间正序/逆序排序。
3. 回复反馈的框要和反馈详情框大小适应，现在是很小的一块，需要修改

系统日志功能的研究

告诉我需要修改的代码

### 2025年12月31日 18:00:08

查看 b 站视频，都推荐用宝塔，于是使用宝塔

1. `if [ -f /usr/bin/curl ];then curl -sSO https://download.bt.cn/install/install_panel.sh;else wget -O install_panel.sh https://download.bt.cn/install/install_panel.sh;fi;bash install_panel.sh ed8484bec`
   官网命令下载宝塔面板，报错

2. 使用 AI，完整路径下载
   `wget -O /tmp/install_panel.sh https://download.bt.cn/install/install_panel.sh`
   `bash /tmp/install_panel.sh ed8484bec`
   宝塔启动失败

3. 使用宝塔多机管理-宝塔自带 app 统一管理 SSH 和宝塔面板
   还是失败

4. 开放端口 8888，还是失败

5. AI 认为缺少 psutil 模块和 Python 的 SSL 模块，安装后运行，
   `cd /www/server/panel` 切换宝塔面板目录
   5.1 `curl -sSO http://download.bt.cn/install/install_panel.sh && bash install_panel.sh` 重新安装面板
   失败，定位问题应该就是"缺少 psutil 模块和 Python 的 SSL 模块"
   尝试解决
   5.2 重新下载编译面板的 python3.7.9（这里耗时 40 分钟，太久了）结果还是失败了

6. 是否尝试切换操作系统（查阅宝塔论坛，可能要切换操作系统）
   哈哈哈哈哈更换 debian 系统很快就解决了（应该也是 linux，没什么问题）

7. 尝试将后端 server 放在 pm2 管理，但是一直报错
   一开始说是缺少模块-cors-multer，（看了很多博客都说删除依赖包再上传，就这么做了）
   npm 报错，在终端下载也一直无法连接，能 ping 通，换了好几个镜像
   后面麻木地跟着 AI-报错-重试-报错-重试
   然后把本地 node_modules 压缩上传，继续 npm 下载-重试几次之后终于可以了

8. 让 hfl 使用 postman 访问，404 not found
   发现是端口号还是 3000 没改过来，修改-睡觉

### 2026年1月1日 13:25:43

1. 重新后端 N 次：网站-node 版本管理+pm2 管理+终端 pm 命令+终端 `node app.js` 命令
2. 修改 `.env` 文件，`app.js` 文件--端口-ip
3. 可以访问了
4. 部署前端，显示无法连接后端 API，将所有 vue 文件统一使用 `config.js` 里面的 URL（这里没有全部修改成反引号导致无法正常调用）
5. 可以连接，但 500 报错，数据库无法连接，于是重新修改 `db.js` 和导入数据库，全部修改为 mydb
6. 正常登录，但功能报错（1.无法连接后端 API 2.数据库外键依赖错误-删除用户）
7. 修改前后端文件后导入，一直报错数据库（后面发现 pm2 可以查看错误日志-即数据库报错）
8. 发现是 `db.js` 需要和宝塔面板的数据库保持统一+`1.sql`（数据库名 X）

9. 提交反馈-查看 √ 但是查看详情和删除失败了--结果还有 3000 端口漏网之鱼（看来每个页面的增删改查都要修改一遍）

10. 最后安装 xelatex 环境
```
# 基础 TeX Live + XeLaTeX
sudo apt install texlive-base texlive-xetex
sudo apt install texlive-latex-recommended texlive-latex-extra
sudo apt install texlive-fonts-recommended texlive-fonts-extra
sudo apt install texlive-lang-chinese
```

11. 把常见的图表类型测试一遍没有问题就发到群里了

12. 部署服务器后，有些代码编译会报错（全屏红字）
    编译生成的 pdf 为什么总是两页？我在本地编译的只有一页啊
    附上代码和 pdf 以及正常的 pdf

想偷懒安装 1G 的 xelatex 环境（如 10.所示），结果一堆问题。。还是老老实实下完整版了（还是中等版）3G 左右
完整版有 10G 之多，蒜鸟8

```
# 安装 TeX Live 2024（最新稳定版）
wget https://mirror.ctan.org/systems/texlive/tlnet/install-tl-unx.tar.gz
tar -xzf install-tl-unx.tar.gz
cd install-tl-*
./install-tl
```

13. 安装了 tex live2025 之后反而用不了一点编译了
    而且 xelatex 编译一些简单 .tex 文件也报错（缺少包）
    询问 GPT 解决（修改 `compile.js`--Linux 不支持某些 windows 字体+编译使用绝对路径是最好的）

14. 编译还是一堆问题。明天再搞了 2026年1月2日 00:41:33，现在不能显示部分中文

### 2026年1月2日 15:25:43

编译部分中文无法显示，还是生成 simsun 不支持字体，修改提示词
编译有些图没办法正常显示，
编译报错全屏（崩溃）
修改 `compile.js` `deepseek.js` `chartgenerator.vue`

### 2026年1月4日 19:32:20

关于系统健康。什么时候会出现，实在不行就删掉吧
添加了"登录""调用""编译"成功和失败，插入系统日志。

### 2026年1月5日 10:37:50

修复《测试报告》里面的 bug

**18:00-19:30**
注册之后的登录没有显示到系统日志里面。
调整为注册之后跳转到登录界面（帮助用户记住密码）这样会有新的问题
改动最小的方法：在注册成功之后插入系统日志

还要观察改变密码之后的跳转是否正常，好吧没有做跳转。

**20:00-21:15**
上传失败的报错似乎没有显示（什么时候会失败呢？）
改了之后，上传失败一直转圈圈

然后编写周总结和《测试报告》，先让 wl 改一下吧，然后我那版先放着（改了 2h......唉）

安排期末周时间

添加系统日志按时间排序
本地可以正常显示运行。但服务器一直不显示.....传了好几次（发现即使 http 文件夹删除了也可以正常访问，离谱）
离谱，换个浏览器就可以了
需要清除 chrome 浏览器缓存（shift+ctrl+delete）

最后又发现两个 bug，应该是最后的了吧.......
1. 刷新崩溃需要解决（可以添加到 bug 里面，在宝塔添加 ngnix 配置解决）
2. 发现 edge 浏览器下，密码输入框会有眼睛，如果点击我页面的眼睛它会消失（谷歌不会出现这个问题），
前端禁用解决

### 2026年1月6日

#### 11:20:50
2. edge 浏览器，登录，管理员登录以及改变个人信息（注册已经去掉 type=password 了）
3. 修改密码和重置密码应该添加登出弹窗并跳转--可以参考退出登录导航栏的处理
4. 刷新 bug 添加到文档里

#### 20:11:53
结束，不想再改了。

### 尝试部署后端+前端

错误指令合集？：
是粉红色，你的不是
粉红色折线图

测试这个指令的时候一直有问题
似乎是生成的时候切换其他应用就会连接不稳定报错----本地历史记录 local 没有存储？？

### 2026年7月9日（整合自 CHANGELOG.md）

> 范围：PGFPlotsGenerator 项目（前端 Vue + 后端 Node/Express）

#### 1. 时间筛选结束日期截断修复（同一类型 bug）
- **问题**：结束日期筛选时，MySQL 把 `"YYYY-MM-DD"` 当作当天 `00:00:00`，导致结束日当天 `00:00` 之后的数据被漏掉。
- **修复**：结束日期统一补 `23:59:59`。涉及三处：
  - `backend/routes/AdminNotice.js` — 通知管理（`startDate` 补 `00:00:00`、`endDate` 补 `23:59:59`）。
  - `backend/routes/AdminLog.js` — API 统计与系统日志（`call_time` / `log_time BETWEEN startDate AND endDate + ' 23:59:59'`）。
  - `backend/routes/feedback.js` — 反馈管理（`feedback_time BETWEEN startDate AND endDate + ' 23:59:59'`）。

#### 2. 密码校验规则统一（同一类型 bug）
- **规则**：密码仅包含字母和数字，长度 1-8 位（`/^[a-zA-Z0-9]{1,8}$/`），前后端一致。涉及：
  - `backend/routes/auth.js` — 新增 helper `validatePassword(pwd)`，应用到 `/register` 与 `/change-password`；`currentPassword` 仍只与哈希比对，不套新规则。
  - `src/components/RegisterForm.vue`、`src/views/ChangeInformation.vue` — `placeholder` 改为 `字母或数字，长度1-8位`，新增灰色提示文案与同一正则校验，新增 `.hint-text` 样式；`validateCurrentPassword` 改为仅判非空。
  - `src/components/LoginForm.vue`、`src/components/AdminLogin.vue` — 移除登录表单 `password.length < 6` 拦截，仅保留非空校验，使 1-8 位合法短密码能正常登录。

#### 3. 图表页「新建对话」按钮样式优化 — `src/views/ChartGenerator.vue`
- 去掉 `type="warning" plain` 的橙色小按钮与垃圾桶图标，改为清爽胶囊样式：白底 + 主题靛蓝描边，悬停渐变填充（`#667eea → #764ba2`）+ 阴影，点击轻微下沉。
- 图标换成笔形 `EditPen`（已加入图标导入），文字「新建对话」，对齐 DeepSeek「开启新对话」观感。

#### 4. 新建对话欢迎语截断 Bug 修复 — `src/views/ChartGenerator.vue`
- **问题**：初始加载的欢迎语是完整版（含「如果需要修改AI生成的代码…」），但「新建对话」`clearCurrentSessionMessages` 里的是截断版，导致点新建对话后欢迎语变短。
- **修复**：提取统一常量 `AI_GREETING`，初始加载与新建对话两处共用，从根上消除两段文案再次漂移。

#### 5. AI 模型调整
- AI 集成：单一 DeepSeek → **DeepSeek-v1 + Qwen3.5 双模型**；路由 `deepseek.js` → `chat.js`，以前的有关AI都统一命名为deepseek，现在已经根据实际模型命名。

#### 6. Qwen 分支超时补充 — `backend/routes/chat.js`
- 给 Qwen 分支的 OpenAI 客户端补充 `timeout: 30000`，与 DeepSeek 分支一致，避免请求无限挂起。

##### 备注（本次未改，留待后续）
- 前端 `config.js` 的 `API_BASE_URL` 仍为硬编码（含注释掉的服务器地址），可改为 Vite 环境变量 `VITE_API_BASE_URL`。
- `db.js` 数据库凭据硬编码（`localhost/root/000/X`），且 `.env` 中 `DB_*` 为占位符；若要改读 env 需先确认真实凭据。
- `AdminUser.js` 重置密码硬编码 `666666` 且响应回显明文，可提为环境变量常量。
- 密码长度下限 1 位偏弱，建议后续提高到至少 6 位。

---

### 2026年7月13日

> 范围：PGFPlotsGenerator 项目（前端 Vue + 后端 Node/Express）

#### 1. 消息中心未读/已读功能
- **新建** `notice_read` 表（`user_id` + `notice_id` 唯一索引），实现每用户独立的已读/未读状态跟踪，`INSERT IGNORE` 保证幂等。
- **涉及**：`migrations/create_notice_read_table.sql`、`backend/routes/notice.js`（新增 `POST /read/:id`、`POST /read-all`、`GET /unread-count`，重写 `GET /` 为 `LEFT JOIN notice_read` 求 `isRead`）、`src/views/MyNotice.vue`（统一调 `/api/notice`，标记已读双向同步）、`src/views/AdminNotice.vue`（新增"已读人数"列）、`backend/routes/AdminNotice.js`（列表查询新增 `read_count` 子查询）。

#### 2. 反馈回复统一为 notice 消息
- **问题**：前端需分别查 `notice` + `feedback` 两张表再拼接，架构割裂。
- **修复**：管理员回复反馈时 `INSERT` 一条定向通知到 `notice` 表（`feedback_id` 唯一索引幂等），前端单源查 `/api/notice`。
- **涉及**：`migrations/add_notice_feedback_columns.sql`（`notice` 表加 `target_user_id`、`feedback_id`、`feedback_time`、`feedback_type`、`reply` 5 字段 + 历史回填）、`backend/routes/feedback.js`（`PUT /:id/reply` 回复后写通知）、`backend/routes/notice.js`（移除 JOIN feedback）、`src/views/MyNotice.vue`（删除旧的双源拼接逻辑）。

#### 3. 管理端通知管理排除反馈通知
- **问题**：管理端通知列表混入了反馈回复通知。
- **修复**：`backend/routes/AdminNotice.js` 全部 7 个端点（列表、详情、更新、删除、批量删除、统计）统一附加 `feedback_id IS NULL` 条件。

#### 4. 反馈回复计入未读 + 时间/标题显示修复
- **问题**：(1) 反馈通知时间显示"未知时间"；(2) 标题出现"BUG反馈反馈"重复拼接；(3) 日期筛选结束日当天数据丢失。
- **修复**：`notice.js` 反馈通知优先用 `feedback_time`；`MyNotice.vue` 新增 `format_ago_Time`（刚刚/分钟前/小时前/天前）；`feedback.js` 回复用 `typeTitleMap` 映射替代旧拼接；`AdminNotice.js`、`feedback.js` 结束日期统一补 `23:59:59`。

---

### 2026年7月14日

> 范围：PGFPlotsGenerator 前端（Vue3 + ElementPlus）
> 主题方向（用户裁定）：白底为主 + 电光蓝 `#4D6BFE` 单一功能色，参考 DeepSeek 主页

#### 1. 统一设计令牌（单一视觉真相源）
- **新建** `src/styles/tokens.css`：集中定义全站 CSS 变量（`--brand`/`--brand-gradient`/`--bg-*`/`--text-*`/`--success/warning/danger/info`/`--radius-card`/`--shadow-*` 等），作为唯一色值来源。
- **修改** `src/style.css`：引入 `tokens.css`，全站组件统一引用变量。

#### 2. 共享基础组件
- **新建** `src/components/ui/`：`AppCard.vue`、`AppButton.vue`、`AppSpinner.vue`、`EmptyState.vue`、`CodeBlock.vue`（代码高亮 + 行号 + 复制）。
- 复用情况：`MyNotice.vue`、`MyHistory.vue`、`ChartGenerator.vue` 等已接入 `EmptyState`/`AppSpinner`/`AppCard`/`CodeBlock`，消除手写空态/卡片/Spinner/复制逻辑。

#### 3. Emoji 弃用 → ElementPlus 图标
- 全站 emoji（🔔📊📋📁💬🏠👤📢📝🗑⚙️ 等约 25 处，分布于 `CommonNavbar`/`CommonSidebar`/`AdminSidebar`/`AdminNavbar`/`ChartGenerator`/`MyNotice`/`DataUpload`/`ChangeInformation` 等 12 文件）统一替换为 ElementPlus 线性图标（激活态使用品牌蓝）。**扫描验证：src 内 0 处 emoji 残留。**

#### 4. 非品牌色收敛
- 原 `#3498db`/`#409eff`/`#dc3545`/`#2ecc71`/`#9b59b6` 等散点硬编码色值统一收口为品牌蓝与标准功能色。**扫描验证：0 处残留，21 文件改用 `var(--brand*)`。**
- 涉及：`TheAuth.vue`（原紫渐变背景 → `--brand-gradient`）、`LoginForm.vue`/`RegisterForm.vue`/`ChangeInformation.vue`（红绿错误/成功色 → `--danger`/`--success`）、`CommonNavbar`/`CommonSidebar`/`Admin*` 系列导航与侧边栏。

#### 5. 首屏模板引导（P0 核心闭环）
- `src/views/ChartGenerator.vue`：用户未发送消息时展示折线图/柱状图/饼图/散点图模板卡片，一键填充示例提示词（保留 `生成PDF` 编译出图能力）。条件（AI_message=0）已修正目前正常

#### 6. 响应式与信息架构
- 会话（原 `localStorage` 临时）与历史（服务端）概念归一：`ChartGenerator.vue` 消息持久化已迁移至服务端（`/api/conversations`），新增/切换/重命名/删除对话均走服务端接口，UI 上不再混用本地存储与历史记录，自洽无歧义（`src/views/ChartGenerator.vue` L612 起）。
- 对话界面侧边栏模仿deepseek侧边栏（历史对话）重命名和删除图标太小，使用选择器覆盖样式已修正目前正常
- 对话列表面板移动端抽屉化（`ChartGenerator.vue`：`max-width:768px` 下 `conversation-sidebar` 固定定位 + 遮罩）。
- 统一代码预览（`CodeBlock`）、空状态（`EmptyState`）、加载态（`AppSpinner`）。

#### 7.备注(造成bug后续修复)
- 对话内改码重编译+编译失败 AI 修复--需要出现这些情况才显示。没有使用场景，相关代码已删除。
- 快速开始界面优化。预置的提示词优化。首屏引导区（新增 Hero + 三步走 + 模板卡片）预置提示词（中性 + 专业化）把快速开始界面居中放置
- 单独文件没有提示词的情况可以点击发送按钮。禁止在缺少提示词（inputMessage 为空）时发送消息，无论是否已选择文件。
- 改主题色的时候造成的系统监控-API调用统计界面显示有问题。已恢复原来的样式

---


### 2026年7月15日

#### 侧边栏与导航栏 UI 调整（参考 DeepSeek 设计）

1. **侧边栏重构为历史对话**：侧边栏仅保留历史对话功能，无其他入口；原有侧边栏功能移至用户图标点击展开。整体风格参考 DeepSeek 侧边栏设计。
2. **对话侧边栏可收起**：新增折叠态（`convCollapsed`，localStorage 持久化）与 `toggleSidebar()`；桌面端点击左上 `Menu` 按钮将侧栏宽度收为 0、聊天区平滑占满；移动端切为覆盖抽屉，互不干扰。聊天头部新增常驻「新建对话」按钮。
3. **修复整页滚动**：结果：对话消息区与历史侧栏各自内部上下滚动，整页不再移动。
4. **修复前端编译报错（ESLint `no-unused-components`）**：清理未使用的组件注册（如 `MoreFilled`），消除 lint 报错。
5. **导航栏用户区精简与位置调整**：移除右上角用户名旁冗余的三点图标（`MoreFilled`），用户名仍作为下拉菜单触发器；放大用户名文字（`14px→16px`）与点击热区；「通知铃铛 + 用户区」整组从最右侧内移、与右边框拉开距离。

#### 修复与尝试重构
- 默认模型改成Qwen3.5和我买的deepseek-v4-flash（deepseek是自购要花钱）
- 修复aiResponse=null/finalChartCode=null(编译回复为空)的问题
- 去除不必要的ID显示。用户/通知/反馈/历史四类列表。修复系统日志的ID。
- 管理员端每个页面把整页滚动改为内部上下滚动
- 系统监控界面：用错密码登录-warning ；触发 AI 空回复/编译失败/DB异常 - error ；去除统计显示。
- 参考deepseek网页页面，优化重构所有页面（太丑了，太简陋了），给出当前项目前端优化重构方案。最后改了2h效果不是很好。。。。。看不出来有哪里变好了，不舒服的一眼就看出来了。三处标题白字修复（根因：全局标题规则染黑）TheAuth.vue` 管理员登录按钮出现换行修复

### 2026年7月15日

#### 尝试重构优化项目
- prompt:从第一性原理出发，给出设计方案/解决方案。及时给出修改日志。（上下文要用完了的时候）
- 参考plan.md（比如个人中心）有什么没有实现的，从第一性原理出发，评估必要性给出新方案。
- 按第一性原理砍掉了所有锦上添花:类型筛选/批量删/收藏、头像裁剪/操作日志、消息编辑/引用、批量上传/分类标签、骨架屏、el-form 迁移、通知闪烁动画。
- langchain技术。当 LLM 应用的编排复杂度高到「手写代码会失控」时,用统一抽象来管理它。它的核心模块对应六类复杂度:PromptTemplate	大量动态提示词拼装；Output Parser	需强约束结构化输出；Model 抽象	多模型统一接口；Memory	多轮上下文管理；Retriever(RAG)	需检索外部知识；Agent + Tools	多工具自主决策循环。

#### 更新本周最新readme.md

### 2026年9月3日

#### 修复错误提示弹窗bug
- 已删除：// Message 只显示短句 与 ElMessage.error(msg)（原先会弹出红色、无关闭按钮、内容为完整 XeLaTeX 编译日志的提示）。
- 保留：compileErrorTitle / compileErrorDetails / compileErrorDialogVisible 赋值逻辑 —— 编译失败时仅弹出那个可正常关闭的黑白详情弹窗（含「关闭 / 复制日志」按钮），行为不变。

#### 修复用户提示词过长导致AI回复为空的问题
- 代码改动（仅 hello/backend/routes/chat.js，Qwen 分支）
- 原因：模型默认进入深度思考模式，提示词过长时会把max_tokens耗尽。
- 关闭深度思考 + 提高预算（第 358-365 行）：
新增 thinking: { type: 'disabled' }（关键修复）
max_tokens: 4096 → 8192（安全余量，关闭思考后长代码输出不易截断）
- 空内容日志增强（第 373-384 行）：content 为空时额外打印 finish_reason 与 reasoning 长度，便于后续区分"思考截断/平台无内容"。

### 2026年9月5日

####  优化提示词没有尽头，修复生成图表的各种bug也没有尽头，时间成本太高，不如用户多试几次/换个模型/用户自己迭代上下文发给AI改

#### AI回复超时
在我的热点不稳定的情况下：
- Request timed out.调用千问一直报错
- DeepSeek API调用错误: stream has been aborted
后面稳定了之后，deepseek还是报错

校园网调用：
- 一开始是正常的，后面调用deepseek好像也不行了。特别奇怪
- 很奇怪，报错不一致，以及修复
前端报错：无法连接到DeepSeek API，请检查网络设置。
但后台报错是：DeepSeek API调用错误: stream has been aborted

除了网络还有什么办法使得调用稳定呢？（我个人更倾向于网络问题）

#### 图表位置/渲染有问题
- 图例位置固定预定义角（north west / south east 等），与数据形状无关——递减数据必撞左上图例区。
- 轴外 \node 锚点不可靠——尤其用到 current bounding box/axis description cs 这类相对坐标时，编译器在 frame 阶段和 box 阶段尺寸不一致，导致文字漂出 axis。
- 数据来源 / 副标题等附加文字的占位随意——模型经常用 \node 而不是 \title/\caption/xlabel 之类的标准结构来加这些文字，缺稳定性。
- 使用system prompt约束，实在不行就要从代码层面约束了

#### 在提示词里添加"渲染规则"之后出现了很多bug
- 生成图表形状空白（如没有柱形和折线形，甚至只有标题。。）更换在线编译器显示的正常一点，但也有问题

#### 原有偶尔出现的bug（一直没修好像）
- 数据标注会被遮挡
​- 而且两条折线在一起还会重叠。

所以我不是很想改提示词，改了之后会出现新的问题。啊啊

- 添加了一些渲染规则：
1. 禁止图例出现在数据上方：
2. 禁止在 axis 外添加任何文字：
3.  数据点标记与数值标注成对出现：
4. 数值与坐标轴范围必须单位一致、量级一致：
5. 数据不含误差/区间时不启用 error bars：
6. 输出只保留单个 tikzpicture/axis，禁止浮动体与交叉引用：
7. 如果用户消息明确给出了颜色、字体、图表类型等，一律以用户指定为准，并覆盖上述默认值。

---

### 2026年9月7日（全量 P0 安全加固，README/architecture §8 已同步）

> 目标：一次性填上 README v3.0 / architecture.md §8 承认的安全待加固项，防止面试/上线暴露硬伤。

1. **管理员后台鉴权**：`/api/admin/*` 四个模块（AdminUser/AdminNotice/AdminLog/AdminStatic）此前无任何服务端鉴权，仅靠前端隐藏。
   - 修复：`middleware/auth.js` 新增 `requireAdmin`（查库校验 `users.role=admin`，不信任 JWT payload 角色声明）；`app.js` 对 4 个前缀统一挂 `authenticateToken + requireAdmin`，路由文件零改动。
   - 前端：Admin.vue / AdminUser.vue / AdminNotice.vue / AdminLog.vue 全部 admin 请求补 `Authorization: Bearer <adminToken>`；新增公共工具 `src/utils/adminToken.js`（getAdminToken/adminAuthHeader）。
2. **PDF 越权 / 静态目录泄露**：删除 `app.js` 的 `/storage` 无鉴权静态托管（原 `history/{uid}/*.json` 与 PDF 都可被枚举访问）。
   - 修复：compile.js 原 `GET /:id/pdf-url` 改造为 `GET /:id/pdf`——按 `history_id + user_id` 归属校验、`storage` 前缀防穿越后 `res.sendFile` 流式返回。
   - 前端：新增 `src/utils/pdf.js`（fetch + Bearer → blob → objectURL）；ChartGenerator / MyHistory 预览改走 Blob，绕开 iframe 无法带 Authorization 的限制，避免把 JWT 拼进 URL。编译接口响应移除失效的 `pdf_url`。
3. **LaTeX 编译注入加固**：compile.js 新增 `validateLatexCode`，编译前拦截 `\write18`/`\shellescape`/`\input`/`\include`/`\openin`/`\read`/`\includegraphics`/`\usepackage`/`\RequirePackage`/`\lstinputlisting`/`\verbatiminput` 及超长代码（>50KB），命中返回 400。
4. **配置与密钥**：db.js 凭据改读 `.env` 的 `DB_*`（缺省回退 localhost/root/000/X），服务器改库不再动代码；`.env.example` 同步。
   - JWT：移除 auth.js ×3 与 middleware/auth.js 共 4 处 `|| 'your-secret-key'` 兜底；app.js 启动校验 `JWT_SECRET` 缺失即 fail-fast。
5. **密码策略**：`validatePassword` 由「字母数字 1-8 位」收紧为「6-16 位」（`/^[a-zA-Z0-9]{6,16}$/`），前端 RegisterForm / ChangeInformation 的 placeholder/hint/正则同步；登录仅非空校验不变。预置管理员 `admin123/666666` 与重置密码 `666666` 均满足新规则，无需迁移数据。
6. **文档同步**：README §6/§7、architecture §2/§3.2.10/§4/§8、development、deployment（nginx 移除 `/storage` 反代）、openapi.yaml（pdf 接口、admin 鉴权说明）已按现状更新。
7. **遗留提醒**：密码 6-16 位字母数字仍未含特殊字符，是「兼容预置账号/控制改动面」的有意取舍；`.env` 含明文密钥须保密；`1.sql` 预置管理员哈希未经明文验证。
8. **db.js env 化暴露的存量占位修复**：改读 `.env` 后，用户 `backend/.env` 中 `DB_PASSWORD=your-mysql-password`、`DB_NAME=your-database-name`（旧模板占位）被真实读取，导致登录报 `Access denied for user 'root'@'localhost'`。修复：`.env` 两行改为本机真实值 `000`/`X`（`.env` 不入 git）；随后用「先 `dotenv.config()` 再 `require('./app')`」的**真实启动路径**跑冒烟 A0-A7 全 PASS（A0 连库成功、无 token 401、管理员 200、普通用户 403、越权 PDF 404），临时脚本与测试用户已清理。





### 2026年9月9日

> 当日会话集中交付：生成等待体验、错误提示体验、AI 生成/编译链路多类修复与首轮全量测试。详见 `docs/manual-test-cases.md` §7.1 与对应代码文件。

#### 1. 生成等待提示 + 可停止
- 思考气泡：等待 AI 生成期间聊天区显示转圈 + 「正在使用 {模型} 生成图表代码，请稍候…」。
- 停止生成：发送按钮生成期变为"停止"方块；前端 AbortController 取消请求，后端 `chat.js` 以 `res.on('close')` 联动中止 Qwen/DeepSeek 上游调用，避免额度浪费与多余落库；生成期防并发发送，组件卸载自动中止。

#### 2. 错误提示体验
- 全局 ElMessage 默认 `duration: 5000ms` + `showClose: true`（可点击关闭，App.vue `el-config-provider`）。
- 编译失败 Toast 不再截断 300 字符，完整展示后端信息。
- 编译失败样本与日志自动留档 `backend/storage/debug/hist{id}_{时间戳}.tex(.log)`，控制台仅提示文件位置。

#### 3. 界面问题修复
- 首屏"快速开始模版"遮挡 AI 助手初始消息并阻断拖拽 → `guide-area` 移入 `.chat-messages` 容器。

#### 4. AI 生成/编译链路修复（双端防御 + 提示词 PE 重构）
- AI 输出完整文档（`\documentclass`/`\usepackage`/`\begin{document}`…）被再包裹 → 提示词禁止输出文档脚手架；`preprocessLatexCode` 无条件清理；编译外壳预置 `pgf-pie`。
- 数值标注旧无效键 `nodes near coords style=` → 改正确键 `every node near coord/.append style={font=\scriptsize, fill=none, draw=none, inner sep=1pt, anchor=south}`，并强制"有 mark 必配标注"（R3）。
- `symbolic x coords` 误用全角逗号 → 强约束英文半角逗号（R7），附正反例。
- 多系列折线标注重叠 → 按系列错开锚点 `anchor=south/north`、字号 `\tiny/\scriptsize`（R8）。
- 提示词整体按 PE 分层重构：输出边界 / 渲染规则 MUST / 正例 / 反例 / 输出前自检。

#### 5. data_name 列长修复
- `data_file.data_name` `VARCHAR(20)→50`（新增 `migrations/alter_data_file_data_name.sql`，存量库已执行；`1.sql` 同步）；`datasets.js` 上传/改名超长兜底截断；`DataUpload.vue` 名称输入框 `maxlength=50`。

#### 6. 测试文档与数据
- 新增 `docs/manual-test-cases.md`（10 个手动测试样例）与 `docs/testdata/`（8 个配套 CSV/XLSX + 2 个无文件用例）；首轮实测 10/10 通过，问题与修复记录见文档 §7.1。

### 2026年9月11日（进阶用例 v1.2 全量测试与修复）

> 对 `docs/manual-test-cases.md` §9 用例 11–32 全量测试：首轮 **11 通过 / 2 存疑 / 9 失败**，修复后复测收敛为 **21 通过 / 1 放弃**（用例 17 堆叠面积图）。逐例现象/原因/解决方案/复测详见文档 §9.5。

#### 1. `compile.js` 后端加固
- 编译外壳加载 pgfplots 子库 `fillbetween` + `errorbars`——覆盖面积图、误差棒图空白。
- `preprocessLatexCode` 加 `code.replace(/，/g, ',')` 兜底替换全角逗号——零成本消除 17/19 两个致命失败。
- 禁用 P1/P4/P5 后处理函数（yshift 注入、X 轴/数据标签 rotate）；P3 简化为 N≥7 时统一替换为比例 `enlarge x limits=0.15`，N≤6 不干预。

#### 2. `chat.js` 提示词增强
- 移除堆叠面积图相关提示词（用例 17，pgfplots 无原生支持且少用）。
- ybar 边距规则：禁止 `abs` 形式，统一比例 `enlarge x limits=0.15`。
- 新增密集柱状图数值缩写规则：`point meta=explicit symbolic` 缩写显示标签（坐标仍真实值、缩写单位与 Y 轴一致）；所有点放在同一 `\addplot` 内统一 `anchor=south`，**禁止拆多个 \addplot**（防柱位错乱）。
- R5 补误差棒正例模板；R3 补单系列密集点标注错开；模板区补面积图（`\closedcycle`）、堆叠柱状图（`point meta` 标原始值）、散点图（只用 `only marks`）。
- **AI 返回内容为空兜底（修「AI 回复为空」）**：DeepSeek-V4-Flash 长推理时 `content` 可能为空（超时/截断），新增三层降级——先 `reasoning_content` 兜底提取代码块 → 同模型重试 1 次 → 切回 Qwen3.5 兜底；全部失败返回明确 `502 "AI 返回内容为空，请重试"`，不再静默返回空。`extractChartCode` 统一提取图表代码（围栏优先，裸 `\begin{tikzpicture}` 兜底防截断）。

#### 3. 前端 bug 修复（`src/views/ChartGenerator.vue`）
- 编译失败时后端返回完整 LaTeX 编译日志（数千字符），直接塞进 `ElMessage.error` 会出现**全屏红字弹窗且无法关闭**。改为截断到 200 字符 + `showClose` + `duration: 5000`，保证可自动/手动关闭；完整日志仍留档 `backend/storage/debug/`。

#### 4. 测试文档
- `docs/manual-test-cases.md` §9.4 结果记录表精简为「用例/结果/备注」三列；§9.5 按 §7.1 结构补全进阶用例「现象/原因/解决方案/复测」记录，写入最终复测结果。

#### 5. 其他bug
- [ ] 还有一个bug在生成的时候前端点击切换模型，也会切换提示，正在使用 Deepseek-V4-Flash 生成图表代码。实际使用模型不变。可以"你的模型是什么"来检验/后端调用也会显示模型。（不用解决）
- [ ] 当数字相差不大且数字达到4位，5位或以上（或者数据很多很密集）时，标注会出现互相遮挡的情况。（扩大单位来解决）

### 2026年9月12日（模型调用统一重构 + 404 修复）

> 把 Qwen / DeepSeek 两套互相独立的调用逻辑统一为「一套 OpenAI 兼容 SDK + 统一超时/重试/兜底」。

#### 1. `chat.js` 模型调用统一重构
- 两个模型统一走 OpenAI 兼容 SDK（DeepSeek 由 `axios` 裸调迁移到 SDK），删除孤立 `axios` import。
- 新增 `MODEL_CONFIG` / `callModelOnce` / `generateWithFallback`：Qwen、DeepSeek 共用同一套参数（`temperature 0.7`）；仅保留差异 `thinking: {type:'disabled'}`（DeepSeek 不发）。
- **统一超时**：单次请求 30s → **45s**。
- **统一输出预算**：max_tokens 统一为 **8192**（原 Qwen 8192 / DeepSeek 4096）。
- **统一降级链对两个模型都生效**：空内容 → `reasoning_content` 兜底提取代码块 → 同模型重试 1 次 → 切换另一模型兜底；全失败返回明确 `502`，不再静默返回空。
- **修复 bug**：原 DeepSeek 分支三层降级的 L3「切回 Qwen」引用了作用域外的 `client`（`ReferenceError`），此兜底此前从未真正生效；重构后按配置动态建客户端，兜底可正常工作。

#### 2. `.env` / `.env.example` 修复 404
- 现象：修改后生成报「服务器内部错误: 404 status code (no body)」。
- 原因：`DEEPSEEK_API_URL` 原为完整 endpoint（`…/v1/chat/completions`），而 OpenAI SDK 会自动在 baseURL 后拼接 `/chat/completions`，两处叠加导致双份路径 → 404。`NSCC_API_URL` 本就是 base，故 Qwen 正常。
- 解决：`.env` 与 `.env.example` 的 `DEEPSEEK_API_URL` 改为 base 语义 `https://api.deepseek.com/v1`（与 NSCC 一致，SDK 自动补路径）。

#### 3. 测试
- 语法检查通过（`node --check`）。
- 真实连通测试：DeepSeek 与 Qwen 均请求成功、不再 404（临时脚本 `_test_ai_conn.js` 已删除）。

---

### 2026年9月13日（ bug 修复与优化）

> 均为实际改动；原因与修复对应如下。

#### Bug 修复

- [x] **后端报错提示不明显 → 改回类 Node 的明确提示，并落库记录调用日志（方便开发调试）**
- 原因：原 `GlobalExceptionHandler` 对未知异常只笼统返回「服务器内部错误」，开发环境难定位；AI 调用也无落库日志。
- 改动：`ChatService` 在 AI 调用成功/失败均写 `api_log`（`recordFailedCall` / `API调用日志已记录`，`ChatService.java:287/347`）；`GlobalExceptionHandler` 对参数校验、缺参、请求体错误、上传超限、兜底 500 均返回具体中文提示，兜底异常同时写 `system_log`，便于查后台日志。

- [x] **已登录用户再登录管理员会出现一系列前端 bug**
- 原因：用户态与管理员态的存储/路由未隔离，切换时残留「半个管理员态」导致外壳渲染错乱。
- 改动：`App.vue` 将用户会话（`token`/`user`）与管理员会话（`adminToken`/`adminUser`）完全隔离；`checkAuthStatus` 检测到「半个管理员态」自动清理（`App.vue:156-159`）；管理员登录只写管理员会话、不触碰用户会话，渲染哪个外壳由「当前路由」决定（`App.vue:184-194`）。

- [x] **Qwen 调用报错（深度思考没关）**
- 原因：Qwen3.5(NSCC) 默认开启深度思考，`reasoning` 1 万+字符、耗时 ~42s，偶发思考跑满 `max_tokens` 只产出残桩。
- 改动：`LlmClient` 对 Qwen 在请求体加 `chat_template_kwargs: {enable_thinking:false}` 真正关闭深度思考（实测 `reasoning=0`、耗时 2.1s）；`disableThinkingOf` 对 `qwen` 返回 `true`（`LlmClient.java:78-83`）。

- [x] **IDE 显示 108 个问题**
- 原因：IDE 的 Java 语言服务跑在 JDK 8，遇到 `var` / `record` / 文本块等 JDK 17+ 语法即报「cannot be resolved」一类假错误。
- 改动：`.vscode/settings.json` 固定 `java.jdt.ls.java.home` / `java.configuration.runtimes` 为 JDK 21（与 `run.cmd` / `mvn-run.cmd` 自动选 JDK 17+ 一致），108 个假错误消除。

- [x] **邮箱改为 2771008024@qq.com 后无法发送验证码**
- 原因：`.env` 的 `SMTP_USER` / `SMTP_FROM` 已改为新邮箱，但 `SMTP_PASS` 仍是对应的旧授权码（或该邮箱 SMTP 服务未开），发信报 535 认证失败。
- 改动：将 `.env` 的 `SMTP_PASS` 更新为 2771008024@qq.com 对应的 SMTP 授权码，发信恢复；`VerificationService.sendRegisterVerificationEmail` 用 try/catch 捕获发信异常并返回「发送验证码失败，请稍后重试」而非原始堆栈（`VerificationService.java:92-97`）。

- [x] **发布通知内容为空时报 `Uncaught runtime errors: [object Object]`**
- 原因：`AdminNotice.vue` 的 `handleSubmit` 直接 `await noticeFormRef.value.validate()`，Element Plus 校验失败时 reject 的是字段错误对象（非 Error 实例），未被捕获形成未处理 rejection，触发 webpack 错误覆盖层；后端本有「标题和内容不能为空」兜底，纯前端问题。
- 改动：`validate()` 追加 `.catch(() => false)`，校验失败仅显示表单内红色提示并中止提交。

#### 优化

- [x] **对话界面左侧历史对话：时间改为「最近一次对话」的时间，排序同此；显示规则为仅当天用相对时间**
- 原因：`ChatService.persistConversation` 插入消息时从不更新 `conversations.updated_at`（仅自动命名时更新该行），侧边栏时间≈创建时间、排序失真。
- 改动：`ConversationMapper.xml` 列表 SQL 新增 `last_message_at` 子查询（`MAX(conversation_messages.created_at)`），排序改为 `ORDER BY COALESCE(last_message_at, updated_at) DESC`；`ConversationVO` 增加 `last_message_at` 字段；前端展示优先取 `last_message_at`（空会话回退 `updated_at`），存量数据无需刷库。显示规则随后调整为：当天（同一自然日）显示 刚刚/N 分钟前/N 小时前，非当天显示具体日期 `YYYY-MM-DD`。

- [x] **侧边栏收起/展开图标与常见 AI 产品风格统一**
- 改动：`ChartGenerator.vue` 左上角按钮图标由 `Menu`（三条横线）改为内联 SVG「左侧面板」图标（圆角矩形 + 左侧分隔线），颜色用 `currentColor` 继承品牌色与 hover 效果，按钮样式不变；同步移除未使用的 `Menu` 图标导入。

- [x] **历史记录时间筛选漏记录（如筛 9.10–9.14 丢失 9.11 记录）**
- 原因：日期过滤在分页之后的内存里做——先按 `history_id DESC` 取当前页 20 条再筛日期，早于当前页的记录永远筛不出来；`total_count` 也只按当前页过滤后的条数统计，分页信息失真。
- 改动：日期过滤下推到 SQL 于分页前执行——`GenerationHistoryMapper.xml` 的 `listFrom` 增加 `generation_time >= #{startTime} / <= #{endTime}` 条件；`selectHistoryPage` / `countHistory` 增加 `startTime`、`endTime` 参数；`HistoryService.list` 先解析日期再传参并删除内存过滤块；`exportCsv` 调用同步适配新签名。`total_count` 与翻页均按过滤后结果计算。

---

## 其他

### 十三周周一会议汇报
本周进度：修复了上周演示的 API 调用不稳定和无法读取 Excel 文件问题，在本地安装 XeLaTeX 环境，目前能在前端调用正常编译并显示 pdf，优化了 AI 对话界面和提示词。
计划本周继续测试 AI 的生成代码功能是否能正确按照用户需求进行生成（需要测试尽可能多的样例），根据反馈修改提示词
问题：后端 AI 的 API 无法读取上下文，无法迭代；尝试换一条路：发送它生成代码想让它修改，但它无法识别代码会报错。
这样有些生成的图表代码无法编译也无法让它解决

### 十四周周一会议汇报
（内容待补充）

---

## 四、老师建议

- 生成 PGFPlots 代码的最佳实践（加入提示词）
- 演示时需要稳定的功能
- 基于我国近五年考研人数的数据，绘制折线图
- 基于我国近五年人口普查的数据，绘制直方图

---

## 五、待定 / 废除项 / 疑问

#### 目前的一些问题（2026.9.5更新）
- 用户等待时间界面没有明显提示，只有一个转圈圈的图标，没有文字提示；用户也不能主动停止生成过程。
- 没有去做迭代读取上下文（如果你不给原代码让它修改颜色等信息）
- 没有去做AI自修正的过程（自己尝试编译不成功自动重试）
- JWT后端有，前端当时因为一直报错没有去做适配（
- 且按最近使用时间排序；以及添加置顶/收藏历史记录功能

### 废除的一些可能优化
1. 图表生成界面应该是有一个，重试的按钮（再发一遍），
   你的文件修改了我的数据库文件（发给他看看），不能修改数据库内容
   应该也要加到 api 调用日志，更新数据库中图表生成历史里面的内容（还是当前 id，并没有创建新的记录）
   
   编译失败让AI自己修复的功能（尝试实现，失败了）
2. 历史记录界面，查看详情应该可以正确显示生成的 pdf（好难实现）
3. 忘记密码和记住密码的功能
4. 登录界面的顶部路径不对。问题在于，在我处于登录注册界面的时候，顶部浏览器路径也还是默认显示 `http://localhost:8080/chart-generator`，或者是退出登录时的界面如 `http://localhost:8080/feedback`，
5. 发现有 bug，数据库存储的路径是 windows 路径 `\` 反斜杠
   实际 URL 是正斜杠 `/`
   所以这里的自己构建 URL 和 `generation_path` 不太一样，查看的时候还是自己构建（这样不统一会有隐患）
   但好像也没必要改，先这样吧

### Question 后端

- 服务器端代码/server应该与本地/backend不一致（由于windows和linux区别），如何保存

还有一些功能要细化，

请你作为一个计算机代码专家，帮我修改代码（指出具体位置）
这是历史记录前端界面。我想增加一个功能： 请修改代码

---

## 六、部署相关

### 将 Vue3 + Node.js 项目部署到华为云服务器
1. 本地 XeLaTeX 编译环境，mysql（宝塔里面好像有）

---

## 七、技术说明与参考

### 说明
1. 下载 texstudio 即可（下载 arm 版本会导致无法运行），overleaf 有免费限制，已经超了
2. 使用 `response=fetch api` 和 `axios` 两种方式（目前没感受到区别）

axios：AI 和历史记录
其他使用：fetch

```
const response = await axios.get(`${API\_BASE\_URL}/datasets`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
```

```
const response = await fetch(`${API\_BASE\_URL}/datasets/${row.id}`, {
      headers: {
        'Authorization': `Bearer ${token}`
      },
      method: 'DELETE'
    })
```

---

# ═══════════════════════ 重构 Java 后端 ═══════════════════════

> **分界线**：以上为 Node.js（Express）版开发记录；以下为 2026-09-13 起 Java 后端（spring-backend）重构及后续记录。

## 八、2026-09-13 Java 后端（spring-backend）重构与后续（整合：重构 / 配套修复 / Node 退役）

### 背景与取舍
- 三份优化文档（`c优化方向评估.md` / `t优化方案清单.md` / `w优化方向清单.md`）原本共同结论是「不建议重写为 Java」（前提：以最小成本换取 RAG 面试讲点）。
- 本次目标切换为「Java 岗面试作品」，故决定**全量重写**为 Spring Boot + MyBatis-Plus；第一阶段只做技术栈等价迁移，**不含 RAG**（RAG/Prompt 工程化/结构化输出/编译任务队列化/评估体系列为后续增量）。

### 范围与原则
- 全量重写 Node/Express 后端：13 个路由、11 张表、JWT 鉴权、AI 生成、XeLaTeX 编译、文件上传、邮件验证码、管理后台。
- **数据库 schema 不变**（MyBatis-Plus 直接映射现有 11 张表，零迁移成本）。
- **REST 路径与入参不变**，仅统一响应体为 `{success,data,message}`；前端同步适配。
- 新工程 `spring-backend/` 与 `backend/` 并存，功能对齐后再退役 Node。

### 交付
- 新增 `hello/spring-backend/`（Spring Boot 3.2.12 + MyBatis-Plus 3.5.5 + Java 17，Maven）。
- 包结构：`common/config/security/entity/mapper/dto/client/util/service/controller`；复杂聚合查询走 `resources/mapper/*.xml`。
- 技术映射：multer→`MultipartFile`、xlsx→POI、nodemailer→JavaMailSender、`child_process.exec(xelatex)`→`ProcessBuilder`、openai SDK/axios→`RestClient`、bcryptjs→`BCryptPasswordEncoder`、jsonwebtoken→jjwt。
- 前端改动：登录/注册/管理员登录读取 `data.{user,token}`；`notice`/AdminNotice 由 `code===200` 改为 `success`；AdminFeedback 列表改为 `data.{records,pagination}`；数据集上传判定改为 `success`；Vuex 未读数判定改为 `success`。

### 验证（实测）
- 应用启动正常（Tomcat :3000）；无 token→401 `未提供token`，非管理员访问 `/api/admin/**`→403。
- 管理员 `admin123/666666` 登录返回 `data.{user,token}`；`/api/admin/static`、`/api/admin/users`、`/api/notice`、`/api/history`、`/api/datasets`、`/api/conversations`、`/api/feedback`、`/api/admin/log/*`、`/api/admin/notices` 返回结构正确。
- 修复 `only_full_group_by` 下的两处按日期分组 SQL（用户注册统计、通知阅读统计）。
- 编译链路实测：`POST /api/compile/82` → `data.{history_id,pdf_path,file_size}`；`GET /api/compile/82/pdf` → 200 流式 PDF；中文 UTF-8 正常。

### 后续修复与配套（整合原「九」「十」两部分）

#### 1. 配置与联调
- `backend/.env`：验证码发件账号由 `3242899892@qq.com` 改为 **`2771008024@qq.com`**（`SMTP_USER` / `SMTP_FROM`，显示名用英文 `PG Admin` 规避 properties 中文编码问题）。
  - **待办**：`SMTP_PASS` 需换成该邮箱的 SMTP 授权码，否则发信报 535 认证失败。
- `spring-backend/application.yml`：新增 `spring.config.import: optional:file:../backend/.env[.properties]`，**与 Node 共用同一份 DB/密钥/SMTP 配置**；邮件默认值同步改为新账号。
- `WebConfig`：启动时打印 uploads / history / charts / debug 及 `generation_path` 解析基准的**绝对路径**，便于核对。
- 结论：Java 与 Node **不冲突** —— 同一个库 `X`（schema 未动）、同一 `backend/uploads` 与 `backend/storage`；仅端口 3000 不能同时启动。

#### 2. 环境依赖排障（同一根因：Maven/IDE 跑在 JDK 8）
- 报错 1：`mvn package` → `无效的标记: --release`。
- 报错 2：`mvn spring-boot:run` → `RunMojo ... class file version 61.0 ... only recognizes up to 52.0`。
- 根因：Maven 自身运行在 JDK 8，而 Spring Boot 3 插件要求 JDK 17+。
- 新增 `build.cmd` / `run.cmd` / `mvn-run.cmd`：自动从 `%PG_JAVA_HOME%` → Microsoft jdk-21 → Java jdk-21 / jdk-17 / jdk-23 中挑选 JDK 17+ 再执行。
- 用户级 `JAVA_HOME`：`C:\Program Files\Java\jdk-1.8` → **`C:\Program Files\Microsoft\jdk-21.0.2.13-hotspot`**（`setx`，系统级未动）。验证：`mvn -v` → `Java version: 21.0.2, vendor: Microsoft`。
- 新增 `.vscode/settings.json`：用 `java.jdt.ls.java.home` / `java.configuration.runtimes` 固定 JDK 21，消除 IDE 里 `DatasetVO cannot be resolved` 一类**假错误**（根因：语言服务按 Java 8 解析，遇到含 `var` / `record` / 文本块的文件即解析失败）。
- 残留（非阻塞）：`java` / `javac` 命令来自系统级 Oracle javapath（Java 23），故启动请用 `run.cmd`，不要手敲 `java -jar`。

#### 3. 验证工具
- 新增 `verify.cmd` + `verify.js`（Node，零依赖）：启动后端 → 鉴权 / 管理后台 / 用户侧 / 数据集增删改查 / 编译链路全量回归。
- **实测：`PASS=27  FAIL=0  WARN=0`**（含真实 XeLaTeX 编译与 PDF 鉴权流式返回）。
- 备查：最初用 PowerShell 写验证脚本，因 PS 5.1 解析异常改用 Node 实现。

#### 4. 文档
- 新增 `spring-backend/README.md`（技术栈、Node→Java 映射、配置优先级、构建运行、验证、共存与退役步骤）。
- 新增 **`docs/plan-AI.md`**：整合 `c` / `t` / `w` 三份文档的**优化路线图（19 项 / 5 批次）**，含 RAG 选型建议（第一版推荐「MySQL 存向量 + Java 暴力余弦」，零新中间件，可演进 pgvector）、面试叙事与验收标准。
- `.gitignore` 补充 `spring-backend/.env`、验证日志、`target/`。

#### 5. Node 后端退役（删除前先核查，删除后复验）
- 删除前全仓引用核查：前端 / spring-backend / 脚本 / launch.json 对 Node 代码 **0 处悬空引用**。
- 归档：`backend/migrations/001_conversations.js` 的建表 DDL 转存为 `migrations/create_conversations_tables.sql` 后再删。
- 删除 `hello/backend/` 下全部 Node 代码：`app.js`、`db.js`、`createAdmin.js`、`routes/`（13 个）、`middleware/`、`services/`、`utils/`、`migrations/`、`package.json`、`package-lock.json`、`yarn.lock`、`.env.example`、`node_modules`；`backend/` 目录原地保留数据。
- 依赖清理：根 `package.json` 用 `npm uninstall` 移除仅为 Node 后端服务的 `bcryptjs` / `cors` / `express` / `jsonwebtoken` / `multer`（lock 同步，removed 50 packages）。
- 文档改写：`README.md`（+v3.3 版本行）、`spring-backend/README.md`（§8 标记已执行）、`.github/copilot-instructions.md`（整篇重写为 Java 视角）、`docs/architecture.md`、`docs/development.md`、`docs/deployment.md`、`docs/manual-test-cases.md` 统一以 spring-backend 为唯一后端；`log.md` 历史流水不动。
- 复验：`verify.cmd` **PASS=27 FAIL=0**（与基线一致）。

#### 6. 残留垃圾清理
- 删除：根目录空文件 `smoke.log`、`backend.log`（Node 时代 EADDRINUSE 报错残留）、`texput.log`（2025-12 编译失败残留）、空目录 `backend/temp/` 与 `hello/uploads/`。
- 保留：`docs/testdata/`（manual-test-cases 32 个用例的配套数据，非垃圾）、`docs/plan-1.md` / `plan-AI.md`（历史规划存档）。

#### 7. 数据目录改名 `backend/` → `data/`（语义更清晰）
- 改动点：
  - `application.yml` 5 处：`spring.config.import` 指向 `../data/.env`，`uploads/history/charts/debug` 四个目录默认值改 `../data/...`；`AppProperties.java` 4 处代码默认值同步。
  - DB：`generation_history.generation_path` 前缀 `backend\` → `data\`（**UPDATE 128 行**，`HEX()` 抽查确认单反斜杠无误）；`data_file.file_path` 按文件名重解析，无需动。
  - 前端 1 处：`ChartGenerator.vue` 编译失败提示文案 `backend/storage/debug/` → `data/storage/debug/`。
  - `application.yml` 打包在 jar 内，改后重新 `mvn package`（`target/` 被 IDE 语言服务器锁定无法 clean，用不 clean 的增量打包）。
- `data/` 最终仅含 `.env` + `uploads/`（33 文件）+ `storage/`（history 251 JSON / generated_charts 165 PDF / debug）。
- 文档路径表述同步：README（+v3.4 版本行）等 7 份。
- 复验：`verify.cmd` **PASS=27 FAIL=0**（其中编译 hist209 + PDF 鉴权流式返回 200，直接验证了新路径解析与历史数据可读）。

### 批次1 主链路落地（A1 RAG + A2 + A3 + RAG CLI，2026-09-13 晚）

> 施工图：`docs/plan-AI-批次1-执行方案.md`（自包含，T0–T13 任务级细节）。决策前置：评估后**不引入 LangChain4j / Spring AI / pgvector**，RAG 自研（MySQL 存 JSON 向量 + Java 暴力余弦，约 200 行）。

#### 1. A1 RAG 检索增强
- 迁移 `hello/migrations/create_rag_vector.sql`（`rag_vector`：source_type / user_id 隔离 / `uk_source_ref` 幂等键 / dim+model 校验字段）。
- 新增 `service/rag/` 五件套：`EmbeddingClient`（RestClient+JDK HttpClient 对齐 `LlmClient`，独立 3s 超时）、`VectorStore`（delete-then-insert 幂等 upsert）、`Retriever`（暴力余弦 + 历史/模板分区 Top-k）、`PromptComposer`（few-shot 段落，显式声明与【上下文独立指令】不冲突）、`RagService`（门面，全链路静默降级）。
- 新增 `config/AsyncConfig`（全工程首个 `@EnableAsync`，rag-index 线程池，队列满丢弃记日志）。
- 接入点仅两处：`ChatService.buildSystemPrompt` 注入 few-shot（关闭时返回 ""，拼接结果与旧版逐字符一致）；`saveGenerationHistory` 后 `@Async` 索引（fire-and-forget）。
- CLI 工具：`tools/RagCli`（seed/backfill/demo，主类 `--rag-cli=` 参数走非 Web 模式）+ `scripts/rag_seed|rag_backfill|rag_demo.cmd`。
- 配置：`app.rag.*` + `app.embedding.*`（默认 SiliconFlow `BAAI/bge-m3`，dim=1024），环境变量经 `data/.env`。

#### 2. A2 Prompt 工程化 / A3 结构化输出
- A2：`PromptTemplates.VERSION=v1.1-rag`，成功/失败 api_log 均落 `prompt_version`（迁移 `alter_api_log_prompt_version.sql`）。
- A3：提示词追加【结构化输出约定】；新增 `util/StructuredOutputParser`（```json 围栏 → 裸 JSON → code 必含 tikzpicture）；解析顺序 JSON → 前端显式 chart_code → `ChartCodeExtractor` 正则兜底；`generateWithFallback` 内无效输出带纠错语重试一次。
- 补充 8 项 JUnit 单测（解析器 5 分支 + 余弦 3 用例）全过。

#### 3. 实测验收（硅基流动 EMBEDDING_API_KEY，全部通过）
- **seed 幂等**：7 条模板（柱状/折线/饼/散点/堆叠柱/密集柱缩写/误差棒）两次重建后 template 分区仍 7 行。
- **backfill**：181 条历史成功案例，成功 181 / 失败 0（约 33s，分批 20 条 + 200ms 限速）；重复执行后 188 行、`(source_type, ref_id)` 全唯一。
- **召回区分度**：相关查询 0.71（历史）/ 0.56（模板），无关查询最高 0.43 —— 阈值 0.55 干净切开。
- **数据隔离**：userId=2（无历史）仅召回模板分区；历史按 user_id=1/4/11/15… 隔离。
- **活体生成**（RAG_ENABLED=true）：日志 `[RAG] 召回 3 条` → DeepSeek 生成 → 返回 `chart_type=bar` + summary + 可编译代码（historyId=219，终验基线中该图直接编译出 PDF）→ `rag-index-1` 线程异步索引成功 → `api_log.prompt_version=v1.1-rag` 落库（call_id=272）。
- **回归**：verify.cmd 三轮 PASS=27 FAIL=0（改造前 / T9 后 / 终验）。

#### 4. 实测驱动的三个修正
- `min-score` 0.72 → **0.55**（`${RAG_MIN_SCORE}` 可覆盖）：旧阈值把 0.71 的强相关历史全卡掉（bge-m3 中文相关对多在 0.5–0.75 区间）。
- 模板 embedText 改自然句式开头（"画一个柱状图：…"），模板召回 0.50 → 0.56。
- 历史行 title 修复（原为 null，现取需求描述截断 50 字）。

#### 5. E1 离线评估（T12，Trae 执行，批次1 闭环）
- 交付审查合格：`spring-backend/eval/eval.mjs`（162 行，零依赖 Node，复用 verify.js 登录，指标口径正确），未触碰任何 Java 代码；误暂存的 `hist47/48.pdf` 已从 git index 清理（`git rm --cached`，仓库尚无 HEAD 不能用 restore --staged）。
- **结果**：`eval/results/rag-off.json` vs `rag-on.json` 各 10 用例——生成成功率 / 可编译率 / **首轮通过率均 1.0（基线饱和）**；avg 延迟 15.4s → 16.8s（**+9%，embedding + few-shot 的量化成本**）。
- 叙事口径（已回填 `plan-AI.md` §12）：不讲「RAG 提升通过率」，讲**评估管线建立 + 基线饱和分析 + 成本量化 + RAG 价值定位（进阶图型/长尾写法）**；附追问预案。
- **评估集 V2 建议**（已登记 `plan-AI.md` §8，作为批次3 E2/E3 前置）：进阶图型组合题 + 含歧义用例 + 静态违例检测（脚本检查生成代码是否命中 R1-R8 反例特征），把「通过」细化为「通过且零违例」，才能量出真实增量。
- **批次1（A1+A2+A3+E1）至此全部闭环。**

#### 6. 批次2 施工图产出（2026-09-13 晚）
- 新增 `docs/plan-AI-批次2-执行方案.md`：覆盖 G1 编译任务队列化（`CompileTaskService` 状态机 + 独立线程池 + `POST /api/compile/{id}` 返回 task_id + `GET /api/compile/task/{taskId}` 轮询）、G2 XeLaTeX 并发控制（Semaphore，`app.latex.max-concurrency` 默认 2）、O1 耗时拆解（`api_log.duration_ms` 迁移 + ChatService 计时 + **替换 AdminLogService 硬编码假 responseTime 245/420** 为真实 avg/P95）。
- 契约变更三处联动（后端端点 + verify.js 第 5 段 + 前端两处 `compileToPDF` 改轮询）必须在同一批落地；回归标准 FAIL=0（PASS 允许 27→28）。
- 明确不做：compile_task 表持久化（演进版）、SSE、A4/G3/G4/O2/O4。

### 批次2 落地（G1 编译任务队列化 + G2 并发控制 + O1 耗时拆解，2026-09-14）

> 施工图：`docs/plan-AI-批次2-执行方案.md`（T0–T8）。规模：后端 9 文件改动 + 1 新建，前端 1 新建 + 2 改动，1 条迁移。

#### 1. G1 编译任务队列化（新契约）
- 迁移 `hello/migrations/alter_api_log_duration_ms.sql`（`api_log.duration_ms BIGINT NULL AFTER prompt_version`，O1 用）。
- 新增 `service/CompileTaskService`：内存任务表（`ConcurrentHashMap`；`MAX_TASKS=500`，超限淘汰最旧终态任务）+ `record CompileTask(taskId,userId,historyId,status,pdfPath,fileSize,error,durationMs,createdAt)`，状态 `queued→running→success/failed`；**不用 `@Async`**（避免同类自调用导致代理失效），直接向注入的线程池 `submit`。
- `config/AsyncConfig` 新增 `compileTaskExecutor`（`compile-task-` 前缀 / core2 / max4 / queue100，保留默认 AbortPolicy）。**任务不可静默丢弃**：`submit` 捕获 `RejectedExecutionException` → 该任务标记 failed + 抛 503「编译任务队列已满，请稍后重试」——与 RAG 池「队列满即丢弃」的语义刻意区分。
- 契约变更：`POST /api/compile/{history_id}` 由同步阻塞改为立即返回 `{task_id, status:"queued", history_id}`；新增 `GET /api/compile/task/{task_id}` 返回终态（success 含 `pdf_path/file_size/duration_ms`，failed 含 `error`），并做 userId 归属校验（越权与重启丢失统一 404 +「服务重启会清空任务状态，请重新提交编译」文案）。PDF 流式下载契约不变。
- `CompileService.parseHistoryId` 提升为 `public static` 供 Controller 复用，避免逻辑复制。

#### 2. G2 XeLaTeX 并发控制
- `app.latex.max-concurrency`（默认 2，`LATEX_MAX_CONCURRENCY` 可覆盖）；`CompileTaskService` 内 `Semaphore` 在工作线程 `acquire()` + `finally release()`，等待时长计入任务 `duration_ms`。两级背压：线程池队列（100）→ Semaphore（2）。
- 顺手清理：`LatexCompiler.validate(String)` 的硬编码 `50000` 参数化——新增 `validate(String, int)` 重载，原重载保留并委托；`CompileService` 改传 `app.latex.max-code-length`（该配置项此前从未被消费）。

#### 3. O1 链路耗时拆解
- `ChatService`：`generateWithFallback` 前后 `System.nanoTime()` 计时（含降级链全部重试时间），**成功与失败两条路径都写 `api_log.duration_ms`**（`saveGenerationHistory` / `recordFailedCall` 各增 `Long durationMs` 形参，三个 catch 分支分别传入）。
- `AdminLogService.apiStats`：删除硬编码假数据 `avg=245 / p95=420`，改为从 `selectApiLogs` 明细用最近秩法（nearest-rank，第 `ceil(n*0.95)` 个样本）算真实 avg/P95 + `sample_count`，无数据时输出 `null / 0`；`AdminMapper.xml#selectApiLogs` 追加 `al.duration_ms`。
- 前端 `AdminLog.vue` **未消费** `responseTime`（仅 L298 声明、模板无渲染），故本批不动它；耗时分位数可视化归批次3 E3。

#### 4. 前端轮询改造 + 下游调用方适配
- 新增 `src/utils/compile.js#submitAndPollCompile`：POST 提交 → 1.5s 间隔轮询（90s 上限 = 30s 编译超时 + 排队余量，单请求 15s 超时）；success 返回任务体、failed 抛后端 `error`、超时抛中文提示。查询阶段失败统一转纯文案 `Error`，避免调用方把「任务已丢失」误判成「历史记录不存在」（提交阶段失败仍保留 `error.response` 供 400/404/503 分支判断）。
- `ChartGenerator.vue` / `MyHistory.vue` 的 `compileToPDF` 改为调用该工具；`compiling` / `compilingId` loading 语义、既有 ElMessage/`compileErrorDialogVisible` Dialog 全部保留。
- **契约变更的回归影响扫尾**：`eval/eval.mjs`（批次1 的 E1 脚本）原先按同步契约读 `data.pdf_path` 判定 `compiled`，契约变更后会把「可编译率 / 首轮通过率」误判为 0；已同步改为「提交拿 task_id → 轮询终态」，避免评估脚本静默失真。

#### 5. 验收（2026-09-14 实测）
- **基线不回归**：`scripts\build.cmd && scripts\verify.cmd` → **PASS=29 FAIL=0 WARN=0**（原基线 27；第 5 段 2→3 项，另加 1 条 `responseTime` 断言）。
- **提交即返回（G1）**：4 个编译任务并发提交，POST 响应 14–19ms（改造前最长阻塞 30s）。
- **并发限流（G2 硬证据）**：4 个任务在 5ms 内全部入队；日志 `[COMPILE] 获得编译许可 … 剩余许可=0` 仅出现 2 次且时刻相同（12:35:37.304），后 2 个任务分别在前两个释放许可时（12:35:39.711 / 40.023）才获许可；终态 4/4 success，`duration_ms` 2408/2719/2404/2221ms（含排队）。
- **O1 落库**：一次真实生成（history_id=240、`chart_type=bar`）→ `api_log` `call_id=293 call_status=success duration_ms=3157`；旧行 292/291 为 `NULL`，符合「迁移前无该数据」语义。
- **O1 聚合**：`GET /api/admin/log/api-stats` → `responseTime={"avg":3157,"p95":3157,"sample_count":1}`，与 `SELECT COUNT/AVG/MAX(duration_ms)` 完全一致。
- **未实跑（约定只给步骤）**：重启语义、队列满 503——步骤见下节。

#### 6. 待手动复现的两项
1. **重启语义**：提交编译拿 `task_id` → 重启后端 → 再查应 404 且文案含「服务重启会清空任务状态」。
   ```cmd
   curl -X POST http://localhost:3000/api/compile/239 -H "Authorization: Bearer %TOKEN%"
   REM 记下返回的 task_id，重启后端后再查：
   curl http://localhost:3000/api/compile/task/ct_xxx -H "Authorization: Bearer %TOKEN%"
   ```
2. **队列满 503**：把 `AsyncConfig#compileTaskExecutor` 的 `queueCapacity` 临时改 1（`maxPoolSize` 同步改小）→ rebuild → 并发提交 >5 个任务 → 应看到 503「编译任务队列已满，请稍后重试」且该任务状态为 failed（不静默丢弃）→ **改回 100 并 rebuild**。

#### 7. 已知取舍与观察
- 重启丢失任务状态是**本批已确认取舍**（`compile_task` 表持久化 + 启动恢复属演进版）。
- ~~观察（未修，非本批范围）：`CompileService` 临时目录名为 `temp_ + System.currentTimeMillis()`…~~ → **已修复（2026-09-14）**，见下节。

#### 8. 并发临时目录缺陷修复（2026-09-14）
- **根因**：`CompileService` 编译工作目录名为 `temp_ + System.currentTimeMillis()`，碰撞条件是「**同一 userId + 同一毫秒**」（与 historyId 无关）；`Files.createDirectories` 对已存在目录静默通过，两个任务会拿到同一目录 → `hist{id}.tex` 互相覆盖、两个 xelatex 共用 `-output-directory` 互相踩踏、任务 A 的 `safeCleanup(tempDir)` 提前删掉任务 B 仍在使用的目录 → B 在 `Files.move(tempPdf, finalPdf)` 抛 `NoSuchFileException`，对外表现为来源不明的 500「编译失败」。批次2 的 `compileTaskExecutor`(core2/max4) + `Semaphore`(2) 让「两个 worker 同时进入 compile」成为常态（前端双击「生成PDF」即可触发），风险由理论变为现实。
- **修复**（唯一改动点）：`Files.createTempDirectory(userStorageDir, "temp_")` 替代手写时间戳 —— JDK 原子创建唯一目录（`SecureRandom` 后缀 + 冲突自动重试），从根上消除「共享可变工作目录」；`safeCleanup` 此后只可能删自己的目录。契约、产物路径（`user{uid}/hist{id}.pdf`）、`relativeBase` 相对路径解析、失败样本落盘位置、4 处 `safeCleanup` 调用点与所有异常分支**均未改动**。
- **验证（2026-09-14）**：
  - 回归：`scripts\build.cmd && scripts\verify.cmd` → **PASS=29 FAIL=0 WARN=0**（无新增断言）。
  - 并发定向：同一 `history_id=240` 每轮并发提交 2 次、共 3 轮（6 次编译），**全部 success**（duration_ms 2204/2210、2224/2840、2376/2390）；`data/storage/generated_charts/user1/` 下**无 `temp_*` 残留**。
  - 窗口命中证据：日志显示每轮两条任务在**同一毫秒**获得编译许可（如 12:42:45.460 两个线程并行编译同一 history），即两次 `compile()` 进入时刻同毫秒——正是原缺陷的碰撞条件。
  - **诚实口径**：修复前该竞争窗口仅「同毫秒」级，未能稳定复现原 500 失败；上述证据证明的是「并发窗口确实被进入且修复后全绿」，**不是**「已复现原 bug」。
- 明确不做（已确认超出本次修复范围）：同 history 提交去重（queued/running 时复用）、启动清理 `temp_*` 残留目录。

### 批次3 落地（E2 失败归类 + E3 质量看板 + 评估集 V2，2026-09-14）

> 施工图：`docs/plan-AI-批次3-执行方案.md`（T0–T8）。规模：后端 5 文件改动 + 2 新建，前端 1 文件，评估 1 新建 + 2 改动，1 条迁移。

#### 1. E2 失败归类（api_log 语义扩展为「AI 链路失败事件日志」）
- 迁移 `hello/migrations/alter_api_log_error_type.sql`（`error_type VARCHAR(32) NULL AFTER duration_ms`，**已应用**）+ `ApiLog.errorType`。
- 新增 `common/ErrorTypes`（7 类常量，避免分类字符串在多处拼写漂移）：`UPSTREAM_API_ERROR` / `UPSTREAM_CONNECTION_ERROR` / `UPSTREAM_UNKNOWN_ERROR` / `EMPTY_REPLY` / `CODE_EXTRACT_FAIL` / `COMPILE_ERROR` / `COMPILE_QUEUE_FULL`。
- **顺带补齐两个既有缺口**：① 空回复兜底（`BAD_GATEWAY`）此前只写 `system_log`、**完全不落 api_log**，失败归类整整漏掉一类；② 「生成成功但提取不到图表代码」此前记 `success`，历史记录无法编译。现在前者记 `EMPTY_REPLY`，后者记 `CODE_EXTRACT_FAIL`，且**在同一条记录内**落为 failed（`saveGenerationHistory` 新增 `errorType` 形参，由分类决定 `call_status`），避免先写 success 再补 failed **双计 total_calls**；同时写入描述「未从生成内容中提取到可编译的图表代码」，让「最近失败调用」表有可读文案。
- 编译类失败：**只在 `CompileTaskService`** 落库（`CompileService.compile` 的全仓唯一调用点），`COMPILE_ERROR`（run 的失败分支，带编译耗时）/ `COMPILE_QUEUE_FULL`（submit 被拒分支）；**不写 `prompt_version`**（编译发生在生成之后，无法得知当次生成版本，写当前版本会误读）。
- 聚合：`AdminMapper.xml#selectApiLogs` 追加 `al.error_type`；`AdminLogService.apiStats` 新增 `errorTypes: [{error_type, count}]`（按 count 降序，空时为 `[]`）。
- **口径声明**：失败计数覆盖**全链路**（含编译失败），因此 `summary.success_rate` 会因编译失败而下降——这是 E2 的设计意图，不是 bug。

#### 2. E3 质量看板（复用既有监控页，不新增端点）
- 后端 `apiStats` 新增 `responseTimeSeries: [{date, avg, p95, count}]`（按日聚合明细 `duration_ms`；无耗时的日期不产生数据点，区间内全无则为 `[]`）；抽出 `p95Of()` 供 `responseTime` 与按日序列共用最近秩法口径。
- **耗时口径修正（执行中发现的偏差）**：api_log 现在同时存 LLM 与编译耗时，首版把编译失败的 `duration_ms`（26ms）也算进了 `responseTime`，把 avg 从 3157 拉低到 1592。已加 `isCompileFailure()` 过滤——**耗时指标只统计生成链路（LLM）**，与批次2「两段耗时不混算」的口径一致；实测复验 `avg=3157 / sample_count=1`。
- 前端 `AdminLog.vue`（**单文件改动**）：新增 2 张卡片（平均耗时 / P95 耗时 + 样本数，null 显示「—」）+「质量看板」区块 2 张图（失败分类分布饼图、按日 avg/P95 双折线）；图表实例改为**惰性创建**（两个容器由 `v-if` 控制，首屏可能不存在）；`ERROR_TYPE_LABELS` 提供中文标签、未收录的新分类显示原始值不透掉。
- **空态三规则**全部实现：`sample_count === 0` 不画图走空态、`avg/p95` 为 null 显示「—」（不被 `|| 0` 兜底）、`errorTypes` 为空不画全 0 饼图。

#### 3. 评估集 V2（把「通过」细化为「通过且零违例」）
- 新增 `eval/violations.mjs`（零依赖）七类静态违例检测：R1 图例位置 / R2 轴外文字 / R3 标注与数据点成对 / R5 误差棒语法 / R6 单图结构 / R7 分类坐标逗号 / R8 多系列标注错开。
- **R4（量纲一致性）明确不做静态检测**：它依赖数据语义标度判断（「万元 vs 元」「人 vs 万人」），正则会大量误报，产出不可信的评估数字 → 保留人工评审。R7 也只查 LaTeX 结构上下文（`symbolic x coords` / `coordinates` 花括号内），**不对全文扫全角逗号**，避免把中文标签里的正常标点误判。
- `eval.mjs`：逐用例对 `chart_code` 跑检测，`cases[]` 增 `violation_free` / `violations`，`metrics` 增 `zero_violation_rate`（通过且零违例 / 总数）与 `violation_counts`（按规则计数），汇总表打印两行、逐用例行加 `viol=`。
- `cases.json`：**原 10 条 id 与文案完全不变**（保证与批次1 的 rag-off / rag-on 历史数据可比），追加 6 条 V2 用例（多系列误差棒 / 双 y 轴 / 12 月密集标注+图例外置 / 歧义表述 / 缺数据 / 堆叠面积）。
- 自检证据（**未跑真实评估**）：两个脚本 `node --check` 通过；`detectViolations` 对空代码与合规样例返回 `[]`、对违约样例命中 R1/R2/R3/R5/R6、对全角分类坐标命中 R7、对多系列同侧标注命中 R8，且「上下错开的正确写法」与含中文正文逗号的合法代码均返回 `[]`（零误报）。

#### 4. 附带修复：编译失败信息为空
- 造数时发现 `LatexCompiler.run` 的 `catch (Exception e)` 把 `ProcessBuilder` 异常（如「找不到 xelatex」）直接吞掉，`output` 为空 → 任务 `error` 与 `api_log.call_error` **双双空白**，用户只看到「编译失败」却没有任何线索。已在该 catch 中追加 `xelatex 执行异常: <msg>`（**仅补错误信息**，不改编译/超时/清理逻辑）。复验：任务 error 与 call_error 均带出 `Cannot run program "no-such-xelatex-cmd"`。

#### 5. 回归断言（T7）
- `verify.js` 新增 2 条**类型断言**（不依赖真实数据、允许空数组，避免为迎合断言而造数据）：`errorTypes` 为数组、`responseTimeSeries` 为数组且元素含 `date/avg/p95/count`。
- 基线：**PASS 29 → 31，FAIL=0 WARN=0**。

#### 6. 验收（2026-09-14 实测）
- 基线不回归：`scripts\build.cmd && scripts\verify.cmd` → **PASS=31 FAIL=0 WARN=0**。
- **COMPILE_ERROR 造数**（`set "XELATEX=no-such-xelatex-cmd"` 后启动）：提交编译 → 任务终态 `failed`、`duration_ms=21`、`error` 带异常原因；连做 3 次 → `api_log` **每次各 1 行**（`COMPILE_ERROR` × 3，无重复落库），`summary.total_calls` 每次 **+1**（**无双计**）；行内 `prompt_version=NULL`、`history_id=240`、`duration_ms` 有值。
- **E3 聚合**：`errorTypes=[{error_type:COMPILE_ERROR,count:3}]`；`responseTime={avg:3157,p95:3157,sample_count:1}`、`responseTimeSeries=[{date:2026-09-14,avg:3157,p95:3157,count:1}]`（编译耗时已排除）。
- 前端生产构建通过（`vue-cli-service build`，仅既有 bundle 体积告警）。
- 造数痕迹已清理：debug 目录下本次生成的 4 个 `hist240_*` 样本、临时脚本与临时日志全部删除（既有 `hist139/140/166` 历史样本保留未动）。

#### 7. 未执行（约定只给步骤 / 需人工确认）
1. **`CODE_EXTRACT_FAIL` / `EMPTY_REPLY` 无法稳定造数**：需上游模型返回「非空但无代码」或「空内容」，本地不能确定性触发。可 `INSERT` 假行只用于验证前端展示，**不等于链路验证**——本批未做，也未声称这两类已通过实测。
2. **`COMPILE_QUEUE_FULL`**：需把 `compileTaskExecutor.queueCapacity` 临时改 1 并二次 rebuild（破坏性），步骤见施工图 §10#6，**未执行**。
3. **前端目视验收**：`cd hello && npm run serve` → `/admin`，确认两张图渲染、日期切到批次2 之前（无 `duration_ms`）时三张空态规则生效（无 0 值柱 / 无空饼图 / 卡片显示「—」）。
4. **评估集 V2 实跑**：`node eval/eval.mjs --tag=v2` 会触发真实 LLM 调用（16 用例），按约定**需使用者确认后自行执行**。

#### 8. 评估集 V2 双轮实跑与前端目视验收（2026-09-14 追加）

**（a）R8 检测器误报修正（重要）**
- 首轮 rag-on 跑出 `zero_violation_rate=0.875`、违例分布 `{R8_LABEL_STAGGER:2}`（`bar_stacked` / `bar_negative`）。逐条比对生成代码后确认**两条都是启发式误报**：
  - `bar_stacked`（history 245）是 `ybar stacked` **堆叠柱图**、X=5——R8 规则原文只针对「折线/曲线、≥2 系列、X≥8 个点」，柱图不在范围内，且 `anchor=center` 对堆叠段是合理写法；
  - `bar_negative`（history 249）是**单系列**柱图，`nodes near coords` 计数被下一行 `nodes near coords align=auto` 撑成了 2。
- 修正 `eval/violations.mjs`：R8 改为「用 `\addplot` 计系列数 + `countXPoints()` 估 X 点数 + 排除 `ybar`」，与规则原文对齐。
- **重放而非重跑**：对 `v2-rag-on.json` 各用例的**已落盘生成代码**（`data/storage/history/1/<historyId>.json` 的 `chart_code`）重跑检测器并回写 `violations/violation_free/metrics`，加 `detector_replay` 标记——**零模型调用**。结果：两条误报消失，**`zero_violation_rate` 由 0.875 修正为 1.0，违例分布 `{}`**。
  > 教训：不查证就会得到一个「虚假的区分度」（0.875）。评估数字的可信度取决于检测器正确性，**误报比漏报更有害**。
- R8 修正后回归自检 5/5 通过：真违例（2 系列折线 X=8 全 `anchor=south`）命中；已错开 / X=5 / 堆叠柱图 / 单系列柱图+`align=auto` 均不命中。

**（b）rag-on 轮结果（有效）**：`eval/results/v2-rag-on.json`
| 指标 | 值 |
|---|---|
| 用例数 | 16（原 10 + V2 6） |
| 生成成功率 / 可编译率 / 首轮通过率 | 1.0 / 1.0 / 1.0 |
| **零违例通过率** | **1.0**（修正检测器后） |
| 违例分布 | `{}` |
| avg / P95 chat 耗时 | 16871ms / 35916ms |

结论：**V2 用例下仍未产生真实违例**，"零违例"维度同样饱和（与批次1 在通过率上的饱和一致）。

**（c）rag-off 轮作废（阻塞）**：`eval/results/v2-rag-off.json`（已写入 `invalid_reason` 标记）
- 16 例**全部生成失败**、耗时 0.7–1.3s 快速失败；应用日志为 `[CHAT] DeepSeek API调用错误: Insufficient Balance`。
- 根因：**DeepSeek 账户余额不足**（第一轮 16 次调用耗尽），**与 `RAG_ENABLED=false` 无关**——已核实 rag-off 轮应用日志无任何 `[RAG]` 行、rag-on 轮每例均有 `[RAG] 召回 3–5 条`，开关切换本身生效。
- 该轮**不可作为 rag-off 基线**；待充值后重跑 `node eval/eval.mjs --tag=v2-rag-off` 覆盖即可（脚本与用例无需改动）。

**（d）副产品：上游错误分类拿到自然样本**
- 这 16 次失败被如实落库：`api_log` 新增 **16 条 `UPSTREAM_API_ERROR`**（`call_status=failed`、`duration_ms` 600–1300ms、`call_error='Insufficient Balance'`）。
- 因此本文件 §7 中「无法稳定造数」的结论**对上游错误类已被自然样本覆盖**（`UPSTREAM_API_ERROR` 由"未覆盖"升级为"有真实样本"）；`EMPTY_REPLY` / `CODE_EXTRACT_FAIL` 仍未覆盖。
- 分类聚合实测：`errorTypes=[{UPSTREAM_API_ERROR,16},{COMPILE_ERROR,3}]`，失败计数与库内一致。

**（e）前端质量看板目视验收（浏览器自动化，已完成）**
- 环境：后端 + `npm run serve`（:8080）→ `playwright-cli` 打开 `/login` → 管理员登录 → `/admin/log`。
- **默认态**：失败分类分布显示「上游接口错误 + 编译失败」两个扇区（中文标签生效）；耗时趋势显示 2026-09-14 两个数据点；卡片 `平均耗时 8744 ms / P95 35216 ms / 样本 33`——与 SQL 交叉验证**完全一致**（`n=33, avg=8744, p95=35216`，已排除编译类行）。
- **空态**（日期切到 2026-09-13：该日 49 行，`duration_ms` 与 `error_type` 全为空）三条规则全部通过：
  1. `sample_count=0` → **`qualityCanvas=0`**（未创建任何画布，无 0 值柱、无空饼图）；
  2. 耗时卡片显示 **「—」+「样本 0」**（不是「0 ms」）；
  3. 两张空态文案出现：**「暂无失败记录」**、**「该区间暂无耗时数据」**。
- 附带观察（非缺陷）：该日趋势图仍显示 4 条失败调用——那是 `call_status` 口径，这些行来自批次3 之前、`error_type` 为 NULL，故不计入失败分类，两者不矛盾。

**（f）评估产物**：`v2-rag-on.json`（有效，含 `detector_replay` 标记）、`v2-rag-off.json`（无效，含 `invalid_reason`）；批次1 的 `rag-off.json` / `rag-on.json` **未被覆盖**。

**（g）遗留**：① 需充值后重跑 rag-off 才能给出 V2 的 RAG 增量；② `EMPTY_REPLY` / `CODE_EXTRACT_FAIL` 仍无稳定造数手段；③ 前端验收截图存放在系统临时目录（未纳入仓库）。

### 批次3.5 落地（模型三通道 + 降级链修复 + 耗时趋势图，2026-09-14）

**（a）模型通道扩为三个 + 前端可选**
- 新增 `app.llm.siliconflow` 槽位（硅基流动，OpenAI 兼容，默认 `THUDM/glm-4-9b-chat`，免费档最大 9B/32K）。
- 前端「当前模型」由两值循环按钮改为**向上展开的三项下拉**（`placement="top"`，避免被页面底部裁切）：Qwen3.5 / GLM-4.9 / Deepseek-V4-Flash。
- 五处散落的二值三元判断（`normalizeModel`/`providerOf`/`labelOf`/`disableThinkingOf`/backup 选择）收敛为
  `PROVIDER_CHAIN` 常量 + `resolve(modelKey)` 单一映射，避免新增通道时漏改。
- `disableThinkingOf` 仍需按槽位区分：只有 Qwen 需要 `chat_template_kwargs`（NSCC 模板层专属），
  误传给 GLM 可能被上游判为非法参数而触发一次方向相反的降级。

**（b）降级链修复（本批核心）**
- 旧实现只在「主通道返回内容为空」时才切 backup；**硬错误（余额不足/限流/5xx）直接从 `callOnce` 冒泡**成用户可见的 5xx——
  这正是上一轮 16 连败「结构性不会自愈」的根因。
- 现在：只对「换通道可能成功」的错误接力（402/408/429/5xx，及消息含 `insufficient balance`/`quota`/`rate limit` 的 4xx）；
  400/401/403 等确定性错误**不重试**，避免把 45s 超时叠成 90s。
- 固定优先级 `qwen → siliconflow → deepseek`，从主通道之后逐个尝试，**到链尾即止（不回绕）**；
  `enabled=false` 或未配置 key 的层整层跳过，绝不用空 key 发请求（那样会被上游回 401 而误记成上游鉴权失败）。
- **已知取舍**：主模型选「硅基流动」时其后再无可用通道（deepseek 已停用），即**无兜底**。按需求确认不做环形回绕。

**（c）通道可停用（一行开关）**
- `AppProperties.Provider` 新增 `enabled`（默认 true）；`.env` 加 `DEEPSEEK_ENABLED=false` 即停用 DeepSeek（余额为 0）。
- **不删除 key**：保留凭据线索，充值后改回 `true`（或删除该行）即恢复，无需改代码。
- DeepSeek 在前端**保留可选**（为充值后预留）；选中会返回 400 并说明恢复方法，**不静默改用其它模型**——
  否则用户会误以为自己用的是 DeepSeek。

**（d）降级可观测**
- `FallbackResult` 增加 `usedModel`；响应 `data.model_used` 与历史 JSON `metadata.model_used` 记录**实际完成通道**；
  降级成功打 `log.warn` 并写系统日志（不记录 key）。
- `api_log` 表结构与 E2 七类归类口径**未改动**，不污染批次3 已建立的失败分类。

**（e）耗时趋势图（方案 a）**
- 实测定位根因：**图例并未消失，而是 `legend.top:24` 与居中 `title` 垂直重叠被压住**（放大 canvas 后可见标题下方被遮的图例标记）。
- 修复：图例移至右上角（`top:10, right:'5%'`）；区间仅 1 天时两个数据点直接标数值（多日不加标签，避免密集遮挡）。
- 复测：单日 → 图例可见 + `35216`/`8641` 两个数值标签；多日（注入 3 天数据）→ 双折线正常、无标签拥挤。

**（f）验证（自动化，PASS=39 FAIL=0）**
- `verify.js` 新增第 6 节「降级链」：内置 Node `http` stub（按请求体 `model` 返回 500/401/正常，并统计各 model 调用次数），
  以命令行属性注入临时实例（:3100/:3110），**零密钥、零外网**。
- 新增 8 条断言：① 可降级 500 → 接力成功且 `model_used=siliconflow`；② 不可降级 401 → 请求失败且备用通道**调用次数 0 增量**；
  ③ 不传 `model` → 回落主通道（回归防护）。
- **验证脚本抓出一个真实回归**：`resolve` 里 `List.of(...).contains(null)` 会抛 NPE（旧实现 `"qwen".equals(model)` 对 null 安全），
  导致**不传 `model` 的请求直接 500**。已修复并加用例 ③ 固化。

**（g）Qwen 双轮评测（唯一有效基线，与批次1/批次3 基线不可比）**
- `eval.mjs` 新增 `--model=`（默认 `qwen`）；结果 JSON 记录 `model`、每例 `model_used`、`metrics.degraded_count`。
- 产物：`eval/results/v2-qwen-rag-on.json` / `v2-qwen-rag-off.json`
  （**未覆盖**批次1 的 `rag-off.json`/`rag-on.json`，也未删已作废的 `v2-rag-off.json`）。

| 指标 | rag-on | rag-off |
|---|---|---|
| 生成成功率 / 可编译率 / 首轮通过率 | 1.0 / 0.938 / 0.938 | 1.0 / 0.938 / 0.938 |
| 零违例通过率 | 0.938 | 0.938 |
| avg / P95 chat 耗时 | 9519ms / 14310ms | 7567ms / 17200ms |
| 降级发生次数 | 0 | 0 |

- **本轮 RAG 未体现收益，反而增加约 26% 平均耗时**（9519 vs 7567ms），两轮通过率相同。如实记录，不做修饰。
- 两轮唯一失败均为 `bar_dense`，**根因完全相同且可复现**：Qwen 输出的 `chart_code` 中换行是**字面 `\n` 字符串**而非真实换行，
  整段 `tikzpicture` 挤成一行致 XeLaTeX 语法错误（失败样本 `data/storage/debug/hist265_*.tex`、`hist281_*.tex` 可复核）。
  **非环境问题**（编译任务串行提交、1.4s 正常失败），故评测数字可信。
- **遗留改进项（不在本批范围，未做）**：代码提取阶段还原字面 `\n`/`\t` 转义序列，预计可将该例拉回通过。

**（h）本轮未覆盖（如实记录，不用假数据充数）**
- `EMPTY_REPLY` / `CODE_EXTRACT_FAIL`：仍无稳定造数手段。
- `COMPILE_QUEUE_FULL`：需临时改 `queueCapacity`，本批未做。
- 降级链的真实供应商侧验证：本批用 stub 验证了**链路语义**（可降级/不可降级/回落），未制造真实供应商故障。

**（i）git 状态更正（此前记录有误）**
- **`hello/` 是独立 git 仓库**（15 个 commit），批次 0-3 基线已由 `0a0f590` 提交，本批改动在其之上。
- 外层 `PG` 目录**自身零 commit**，且把 `hello` 记为嵌套仓库引用（mode `160000`）；其暂存区已 `git add` 了 `.codebuddy/plans/*` 等文件。
- **本批改动提交在 `hello` 仓库内**；`data/.env`（含新 key）已被 `hello/.gitignore` 忽略，不入库。
  **任何文档 / 计划文件均不含 key 明文。**

### 批次3.6 落地（压平转义还原 + 图表生命周期 + 编译队列满验收，2026-09-14）

**（a）压平转义还原（本批核心，修复 `bar_dense`）**
- 根因：模型把换行输出成**字面 `\n`**（反斜杠 + n 两个字符）而非真实换行，整段 `tikzpicture` 挤成一行，XeLaTeX 直接报错。
- 新增 `ChartCodeExtractor.normalizeEscapes`，接入**两条提取路径**：`ChartCodeExtractor.extract` / `extractFencedBlock`，以及 `StructuredOutputParser.parse` 的 `code` 出口（Jackson 只解一层转义，双重转义会漏到下游）。
- **关键约束**：不能朴素替换——`\node` / `\newcommand` / `\newline` 与 `\text` / `\times` / `\tikz` / `\tiny` 等合法命令都以 `\n` / `\t` 开头。实现用「后面不跟 ASCII 字母」的 lookahead + 「前面不是反斜杠」的 lookbehind 双重保护。
- 新增 `ChartCodeExtractorTest`（7 例）：还原正确性 + `\node`/`\text`/`\\`+n 等误伤反例，`mvn test` 7/7 通过。
- **实测效果**：重跑 rag-on 一轮（`v2.1-qwen-rag-on`）→ **首轮通过率 0.938 → 1.0**、可编译率 1.0、零违例率 1.0，`bar_dense` 转为 PASS，平均耗时 8605ms（此前 9519ms）。产物未覆盖任何既有结果文件。

**（b）AdminLog 图表生命周期**
- 缺陷比原先记录的更深：不只缺 `onUnmounted`，三个 `ResizeObserver` 此前都是各函数内的**局部变量**，函数返回即失去引用、根本无法 disconnect。
- 修复：新增 `resizeObservers` 登记表；`onUnmounted` 中**先 disconnect 观察者、再 dispose 三个 ECharts 实例**（顺序反了会让已销毁实例被 resize 回调触发而抛错）。
- 顺带清理 `.chart-container` 空类名（该 class 无任何样式规则、也无其他引用）。
- 验证：SPA 内往返切换 5 轮，canvas 数稳定为 3、**0 errors / 0 warnings**。**如实说明**：内存释放本身无法在浏览器端直接量化观测，此处验证的是生命周期钩子正确执行、dispose 无异常、功能不回退。

**（c）COMPILE_QUEUE_FULL 验收（挂了两批的未闭环项，现已闭环）**
- 障碍：`AsyncConfig` 的 `queueCapacity(100)` 是硬编码，满载阈值 = `maxPoolSize(4) + 100 = 104`，不改成可注入就只能靠「临时改源码」验收。
- 改动：`queueCapacity` 提为可注入（`app.compile.queue-capacity`，环境变量 `COMPILE_QUEUE_CAPACITY`，**默认 100 不变**）。
- 验证（`verify.js` 第 7 节）：临时实例注入 `queue-capacity=1`，同刻并发提交 12 个编译请求 → **确实触发 503**、响应带「队列已满」文案、且**被拒任务已落库 `COMPILE_QUEUE_FULL`**（以 `api-stats` 前后计数确认）。「任务不静默丢弃」由此从口号变为硬证据。

**（d）回归**
- `verify.js` **PASS=42 FAIL=0**（批次3.5 为 39，新增 3 条队列满用例；原 39 项无回退）。

**（e）外层 `PG` 目录的 git 边界（此前记录有误，已更正）**
- 事实：`PG/` 自身零 commit、且**没有 `.gitignore`**，却把 `hello` 记为 gitlink（指向旧 commit `80be36e`），并已 `git add` 了 44 个条目（40 个 `.codebuddy/plans/*` + `.trae/rules/*` + `.vscode/settings.json` + `hello`）。
- 处理：新增外层 `.gitignore`（忽略 `hello/`、`.codebuddy/`、`.trae/`、`.vscode/`），并把上述 44 个条目全部 `git rm --cached` 移出索引。**工作区文件一个都没删**，两个仓库的提交历史均未被触碰。
- 现状：外层索引为空，`git status` 只剩一个未跟踪的 `.gitignore`（未跟踪的 `.gitignore` 同样生效）。

**（f）git 提交**：本批改动提交在 `hello` 仓库；外层不做任何提交动作。

**（g）仍未覆盖（如实记录）**
- `EMPTY_REPLY` / `CODE_EXTRACT_FAIL`：仍无稳定造数手段。
- 降级链仍未做真实供应商故障验证（stub 验证的是链路语义，非真实厂商故障）。

### 待办（更新于 2026-09-14 晚）
- ~~批次 1 主链路（A1 RAG + A2 Prompt 工程化 + A3 结构化输出 + RAG CLI）~~：**已完成并全部实测验收**（见上「批次1 主链路落地」）。
- ~~E1 离线评估（T12）~~：**已完成**（Trae 执行，两轮 10 用例 first_pass_rate 均 1.0 基线饱和、avg 延迟 +9%；批次1 全部闭环，见上「5. E1 离线评估」）。
- ~~批次 2（G1 编译任务队列化 + G2 XeLaTeX 并发上限 + O1 链路耗时拆解）~~：**已完成并实测验收**（verify PASS=29 FAIL=0；并发限流、O1 落库与聚合均留硬证据，见上「批次2 落地」；重启语义/队列满 503 两项按约定只给复现步骤）。
- ~~批次 3（E2 失败归类 + E3 质量看板 + 评估集 V2）~~：**代码、断言与人工验收均已完成**（verify **PASS=31 FAIL=0**；COMPILE_ERROR 造数实测；V2 rag-on 轮 16 例首轮通过率 1.0、零违例率 1.0（修正 R8 误报后）；前端质量看板两张图与三条空态规则经浏览器自动化实测通过。详见「批次3 落地」§8）。
- ~~批次 3.5（模型三通道 + 降级链修复 + 耗时趋势图）~~：**已完成并实测验收**（verify **PASS=39 FAIL=0**；降级链三条路径由本地 stub 断言；耗时趋势图单日/多日经浏览器自动化复测；Qwen 双轮评测产出 `v2-qwen-rag-on/off`。详见「批次3.5 落地」）。
- **DeepSeek 已停用**（账户余额为 0）：`.env` 设 `DEEPSEEK_ENABLED=false`，充值后改回 `true` 即恢复（无需改代码）。**不再需要重跑 `v2-rag-off`**——统一改用 Qwen 后，批次1 的 DeepSeek 基线（`rag-off.json`/`rag-on.json`）与当前结果不可比，已作废的 `v2-rag-off.json` 保留作诚实记录。
- ~~批次 3.6（压平转义还原 + AdminLog 生命周期 + COMPILE_QUEUE_FULL 验收 + 外层 git 边界）~~：**已完成并实测验收**——`verify.js` **PASS=42 FAIL=0**；rag-on 重跑首轮通过率回升至 **1.0**（`bar_dense` 转为 PASS）；队列满 503 与 `COMPILE_QUEUE_FULL` 落库均有硬证据；`AdminLog.vue` 两处既有缺陷已修（SPA 往返 5 轮 0 错误）；外层 `PG` 索引清空并新增 `.gitignore`。详见「批次3.6 落地」。
- ~~批次 3.7（生成质量硬修复 + RAG 同题断链）~~：**已完成并实测验收**——`verify.js` **PASS=42 FAIL=0**；定位并切断了「RAG 把上一次的**完整代码**当范例喂回模型」的自污染循环（同题断链 + 入库准入 + purge 清理 9 条）；`LatexCompiler.preprocess` 新增五步确定性修复（空 axis / 直方图补 ybar / ymax 覆盖 / 非法 color / at 括号）；提示词升至 `v1.4-pie-no-axis`；硅基流动模型名换成在架的 `THUDM/GLM-4-9B-0414`。详见「批次3.7 落地」。
- ~~字面 `\n` 致 `bar_dense` 编译失败~~ → **已修复**（批次3.6：`ChartCodeExtractor.normalizeEscapes`，首轮通过率回到 1.0）。
- ~~`COMPILE_QUEUE_FULL` 未验收~~ → **已验收**（批次3.6：容量可注入 + 并发 12 请求触发 503 并落库）。
- ~~`AdminLog.vue` 无 `onUnmounted` 与 `.chart-container` 空类名~~ → **已修复**（批次3.6）。
- ~~外层 `PG` 目录 git 状态混乱~~ → **已整理**（批次3.6：44 个条目移出索引 + 新增外层 `.gitignore`；未删任何文件、未改任何历史）。
- 仍未覆盖：`EMPTY_REPLY` / `CODE_EXTRACT_FAIL` 无稳定造数手段；降级链未做真实供应商故障验证（stub 验的是链路语义）。
- 仍明确不做：前端 DeepSeek 选项置灰（按需求保留可选）；环形降级；耗时趋势图 (b)/(c) 方案。
- 批次3.7 遗留：那 5 条 `直方图` 污染向量未清理（已被同题断链拦住，换措辞仍可能召回）；`\%` 仅靠提示词规则；编译失败仍以"PDF 是否存在"判定（致命错误可能静默产出空白 PDF）；~~结构合规检测仍散在三处且 `eval/violations.mjs` 落后于提示词（仅 R1–R8）~~ → **已对齐**（下节：补 R9/R12，R4/R10/R11 有据不做；三处仍分散但口径一致）。
- ~~待修隐患：文档外壳 `\usepackage{xcolor}[dvipsnames,svgnames]` 选项位置错误~~ → **已修复**（改为在 `\usepackage{pgfplots}` 之前 `\PassOptionsToPackage{dvipsnames,svgnames}{xcolor}`；xelatex 对照实测：`SteelBlue/MidnightBlue/TealBlue` 由 `Undefined color` 转为正常渲染，`Missing character ... in font nullfont` 消失，无 `Option clash`，`verify.js` 仍 PASS=42）。
- 可选：种子模板库扩充（当前 7 条，冷门图型召回为空）。
- 脚本归档：`build.cmd` / `run.cmd` / `mvn-run.cmd` / `verify.cmd` 已移入 `spring-backend/scripts/`（路径已适配新位置）。

### 批次3.7 落地（生成质量硬修复 + RAG 同题断链，2026-09-14）

**（a）根因：RAG 自污染循环（本轮核心发现）**
- 现象：输入"折线图"→ 两个 `\addplot` 坐标完全相同；输入"直方图"→ 面积覆盖图。**重试结果不变**，且 qwen 与 siliconflow 两种模型输出**逐字节相同**。
- 根因：`RagService.indexHistoryAsync` 把每次生成的**完整代码**入库（`content = "需求：X\n代码：\n…"`）；同题查询相似度 ≈ 1.0 → 必然排第一 → 模型直接照抄上一次的结果（**连同上一次的错误**），形成自我强化循环。
- 硬证据：`rag_vector`（user15，`embed_text=' 直方图'`）同题记录 5 条 —— 317 / 323 / 339 / 340 / 341，全部无 `ybar`；时间线 19:41 → 19:52 → 20:22/20:23 **逐次复制**。

**（b）RAG 同题断链（两道闸门，只作用于 history 分区）**
- `Retriever.retrieve(userId, query, queryVec)`：闸门1 —— 历史 `embed_text` 与提问**文字相同**（去首尾空白）即剔除；闸门2 —— 相似度 ≥ `app.rag.max-history-score`（默认 `0.98`，可注入）即剔除。**模板库传 `Double.MAX_VALUE`，通用图型示范不受影响。**
- 实测阈值：`" 直方图"` vs `"直方图"` 余弦 **1.0000**（故闸门2 单独已够，闸门1 是零成本兜底）；`"直方图"` vs `"折线图"` **0.6263**（不误伤其它图型）。
- 端到端：`--rag-cli=demo:直方图:15` **排除 5 条**同题（召回变为 柱状图#324/#334 + 折线图#321）；`demo:折线图:15` 排除 3 条。
- **如实说明**：断链只保证"不再照抄上一次那一份"，**不等于拿到正确范例**；真正保证直方图画成柱状图的仍是提示词规则。

**（c）RAG 入库准入（防固化）**
- 新增 `ChartCodeValidator.hasDuplicateSeries`（两个 `\addplot` 坐标集合相同，忽略顺序与空白）：`RagService.indexHistoryAsync` 与 `RagCli.backfill` 均据此**不入库**。
- 新增 `--rag-cli=purge` + `scripts/rag_purge.cmd`，清理已污染向量：**删除 9 条**（history 258 → 249）。
- 新增 `ChartCodeValidatorTest`（6 例）。
- 未做（按需求选择）：扩准入检测、清理那 5 条 `直方图` 污染记录（现已被断链拦住，但对换措辞的提问仍可能被召回）。

**（d）编译前预处理五步（`LatexCompiler.preprocess`，确定性修复 —— 模型写错也能出对图）**

| 新增步骤 | 触发判据 | 动作 / 真实事故 |
|---|---|---|
| `dropInvalidColorKey` | `color={含逗号}` | **连同整行**删除；`color=` 只接受单色，逗号列表会被 xcolor 当成一个颜色名（每柱报一次错，刷屏 50+ 条并将编译拖到 30s 超时） |
| `braceAtCoordinates` | `at=(x,y)` | → `at={(x,y)}`；未加花括号时逗号被 pgfkeys 当键分隔符 → `Runaway argument` 致命错误 |
| `stripEmptyAxes` | 全图无 `\addplot` | 删除 axis 环境；饼图被 axis 包裹会多画一个 0–1 空坐标框，图元全落在它外面 |
| `addYbarForHistogram` | 含"直方图/频数分布/分布图"且无 `ybar` | 在 `\begin{axis}[` 后插入 `ybar,`；模型只写 `fill=` 却漏写 `ybar` → 渲染成面积覆盖图 |
| `ensureYmaxCoversData` | `ymax` < 数据最大值 | 抬到 `ceil(最大值×1.1)`；未写 `ymax` 或已够大 → **不动**（不做无必要的改进） |

- **本批自身踩坑并修复**：`dropInvalidColorKey` 首版只删键本身、留下一行只有空白的文本；TeX 丢弃行尾空白使"只有空白的行"等价于空行（`\par`）→ 在 axis 选项区中断解析（100 个错）。已改为整行删除，并把该断言写进单测防回归。
- 新增 `LatexCompilerTest`（13 例，此前该类**无任何测试**）。

**（e）提示词与配置**
- `PromptTemplates.VERSION`：`v1.1-rag` → **`v1.4-pie-no-axis`**。新增 **R9**（多系列坐标必须互不相同）、**R10**（直方图/分布图必须用 `ybar`，禁止填充面积）、**R11**（`ymax` 须 ≥ 最大值并留约 10% 余量）、**R12**（文本参数里的 `%` 必须写 `\%`）＋**饼图专项**（禁止 axis 包裹 / 整图只画一次 / 标签与图例只选一种 / 禁止自述性注释）；自检补至 16 条、反例 +7 条。
- `NO_DATASET_SUFFIX` 改为**双分支**：需求模糊（只说"折线图"）→ 可自拟示意数据；指明主题（"近五年 GDP"）→ 必须使用已知权威数据并注明年份与来源。**实测生效**：新生成开始主动标注"数据来源：示意数据（无真实来源）"。
- `SILICONFLOW_MODEL`：`THUDM/glm-4-9b-chat`（**已被平台下架**，调用返回 `30003 Model disabled`）→ `THUDM/GLM-4-9B-0414`；`.env` / `application.yml` / `README` 三处同步。直连实测返回 200。

**（f）前端**
- `ChartGenerator.vue` 新增 `displayText`：助手气泡剔除 ``` 围栏块（latex/json），**并剔除未闭合的尾部围栏** —— 模型输出被 `max_tokens` 截断时末尾 ``` 缺失，只剔成对围栏会把 json 残块留在界面上（history 329 实测）。

**（g）验证**
- `verify.js` **PASS=42 FAIL=0**（自批次3.6 基线起全程无回退）。
- 单测全绿：`LatexCompilerTest` 13 / `ChartCodeValidatorTest` 6 / `RetrieverTest` 7。
- 真实样本端到端：`hist351`（`data/storage/debug/`）预处理后 —— `color` 列表已删、`at` 已补括号、坐标完整保留，xelatex 报错数 **100 → 0**，渲染正常；饼图空 axis、直方图补 `ybar`、`ymax 40000 → 44203` 三项均以真实代码渲染确认。

**（h）未做 / 遗留（如实记录）**
- 按需求未做：编译失败与"PDF 是否存在"解耦（`LatexCompiler.run` 仍以 PDF 存在判成功，致命错误仍可能产出空白 PDF）；`\%` 仅靠提示词规则；清理那 5 条污染向量；把结构合规检测收拢为统一规则集。
- ~~已知隐患：文档外壳 `\usepackage{xcolor}[dvipsnames,svgnames]` 选项位置写错~~ → **已修复**（同上，2026-09-14）。注：xcolor 的 svgnames 名首字母大写（`Coral` 而非 `coral`），`dvipsnames`/`svgnames` 均已生效。
- ~~规则目前散在三处且**已不同步**：提示词文本（R1–R12）、`eval/violations.mjs`（仅 R1–R8，落后于提示词）、`ChartCodeValidator`（仅 R9 的一部分）~~ → **已对齐**（见下节）。

### 规则口径对齐与文档收尾（2026-09-14 晚，面试交付级打磨）

**（a）`eval/violations.mjs` 补齐到提示词口径（本批核心）**
- 新增 **R9**（多系列 `coordinates` 集合完全相同）与 **R12**（文本参数 `title/xlabel/ylabel/legend/\node` 花括号内未转义的 `%`）。
- R9 判定与 `ChartCodeValidator.hasDuplicateSeries` **逐条同源**（按 `\addplot` 切段 → 取第一处 `coordinates {...}` → 点集 `x|y` 去空白 → 单点系列不参与比较），保证「离线评估口径」与「RAG 入库准入门槛」是同一把尺子，不会出现「评估说合规、入库却被拒」的口径分裂。
- R12 只扫文本参数花括号内，**不扫注释行**（注释里的 `%` 是合法 LaTeX 注释，全局扫描会误报）；`\%` 正确识别为合法。
- **刻意不做 R4 / R10 / R11（理由写进代码注释，本身就是可讲的设计取舍）**：
  - R4（数值与坐标轴量纲一致）需理解数据语义标度，正则会大量误报 → 保留人工评审；
  - R10（直方图须 `ybar`）/ R11（`ymax` 覆盖数据）已被 `LatexCompiler.preprocess` **编译前确定性修复**，模型原始输出里的违例在编译阶段即消失 → 静态检测恒为零违例，只会让零违例率**虚高、失去区分度**（评估指标不能自我欺骗）。
- 自测（一次性脚本，跑完已删除）：R9 正例（两条系列坐标相同，含顺序颠倒）/ R9 反例（坐标不同、单点系列、部分重叠）/ R12 正例（`ylabel={准确率（%）}`、`\node` 文字裸 `%`）/ R12 反例（`\%`、注释内 `%`）/ R1、R7 回归 / 空输入 —— **11 例全通过**。

**（b）重跑评估集（新规则版本，旧结果全部保留）**
- 产物 `eval/results/v3.0-qwen-rag-on.json`（`--model=qwen`，本地 `.env` 中 `RAG_ENABLED=true`）：16 例 **生成成功率 / 可编译率 / 首轮通过率 / 零违例通过率 均 1.0**，`violation_counts` 为空（新增 R9/R12 **未命中任何一例**，说明不是靠放宽规则刷出来的 1.0），`degraded_count=0`，avg 6687ms / P95 10969ms。
- 旧结果文件（`v2.1-qwen-rag-on`、`v2-qwen-rag-on/off`、`v2-rag-*`、`rag-*`）**一个都没覆盖**；规则版本变更后跨 tag 不可比，README 引用时已带上 tag。

**（c）文档收尾**
- `hello/README.md` 重写：新增「§5 AI 生成链路」（RAG 检索增强与同题断链、结构化输出 + 双层兜底、三通道降级链、编译前五步确定性预处理与 xcolor 外壳、异步编译队列与并发控制、离线评估集与实测数据）；清掉 Node 时代残留（`db.js`、`backend/.env`、根目录 `plan.md` 引用、本机绝对路径）与过期版本号；版本表补 v3.5 / v3.6 / v3.7。
- `spring-backend/README.md` 同步：`.env` 路径 `..\backend\.env` → `..\data\.env`、脚本路径统一加 `scripts\`、`PASS=31` → `PASS=42`；新增 RAG 分区说明与配置键（`RAG_ENABLED` / `RAG_MIN_SCORE` / `RAG_MAX_HISTORY_SCORE` / `EMBEDDING_*`）；包结构补 `service/rag/`、`CompileTaskService`、`ChartCodeValidator`、`tools/RagCli`；§7.4 补降级链与编译队列的实测结论。
- 四个批次执行方案**内容一字未改**，仅此前提早归档在 `docs/process/` 下。

**（d）回归证据（本批全部实跑，非引用旧数据）**
- `mvn test`：**39 例全绿**（`LatexCompilerTest` 14 / `ChartCodeValidatorTest` 6 / `RetrieverTest` 7 / `ChartCodeExtractorTest` 7 / `StructuredOutputParserTest` 5）。
- `scripts\verify.cmd`：**PASS=42 FAIL=0 WARN=0**（自批次3.6 基线起无回退）。
- 评估集：见（b）；为保证跑的是含 xcolor 修复的最新代码，评估前执行了 `scripts\build.cmd` 重打包（构建成功，`Tests are skipped` 属 build.cmd 既有行为，单测另行执行）。

**（e）未做 / 遗留（如实记录）**
- 按需求未做：`data/storage`、`data/uploads` 的 `.gitignore` 与 `git rm --cached`（运行时产物**仍被 git 跟踪**，本次未动索引）；前端 DeepSeek 选项置灰；环形降级；`ChartGenerator.vue`（2492 行）拆分；前端 request 封装层（G5）与路由守卫（G6）。
- 规则仍由三处并存（提示词文本 / `eval/violations.mjs` / `ChartCodeValidator`）且**靠人工同步**：本次已对齐口径，但新增规则时仍需三处同改，未做统一规则集抽象。

### 2026-09-15 视觉回归硬修复（X 轴标签重叠 / 密集数值标注重叠 / 拆 addplot 柱位错乱）

**背景**：自动化回归（`run-cases.mjs --compile`）发现 5 例编译通过、但**视觉层**出问题。脚本只验"可编译 + 编译成功"，不验渲染，故这些为人工/bbox 检查发现。本次给 `LatexCompiler.preprocess` 新增**三个确定性预处理兜底**，不依赖模型听话。

**（a）X 轴分类标签旋转兜底 —— `fixXTickLabels`（用例 30/31/27）**
- 事故：`symbolic x coords` 分类较多、标签较长时，AI 或未写 `x tick label style`（31）、或写了 `rotate=0`（30），X 轴底层分类名互相压盖。
- 判据：ybar 且按「数量 × 最长标签」综合判断——`(分类数≥6 且最长标签≥3字)` 或 `(分类数≥4 且最长标签≥4字)`。例 25（14 个两字省名）**不**误注入。
- 动作：注入 `x tick label style={font=\scriptsize, rotate=30, anchor=east}`；三种情况收敛——若有非零旋转**尊重作者不动**；若 `rotate=0` 视为事故写法**整体覆盖**；若未写则注入。
- 单测 3 例：长分类注入 / rotate=0 覆盖 / 非零旋转尊重 / 短分类（<6）不动。

**（b）拆 addplot 合并 —— `mergeSplitYbarAddplots`（用例 27 根因）**
- 事故：AI 为区分正/负利润拆成两个 `\addplot`（正系列 5 点、负系列 3 点，x 不全对应）。pgfplots 当多系列并排分配柱位 → `2023Q2/2023Q4/2024Q3` 负值柱被推到错位槽位、挤出可视区，看起来"没显示"。
- 判据（**只合并"同一数据集被拆"，绝不动真多系列**）：ybar 非 stacked、≥2 个 addplot、且所有 addplot 的 x 坐标**两两互不重叠**。
- 动作：取第一个 addplot 选项，把全部坐标合并进单个 addplot → 柱位立即归位，负值柱自然朝下体现正负。
- 单测：合并（断言只剩 1 个 addplot、负值保留、无第二个红色填充）/ 真多系列 x 重叠时不误合并。

**（c）密集柱状图长数字缩写 —— `abbreviateDenseYbarLabels`（用例 25）**
- 事故：AI 在 14 根柱顶写完整长数字（135673）超出柱间距互相压盖；提示词里"密集柱须 point-meta 缩写"规则 qwen 不遵守。
- 判据：ybar 非 stacked、addplot 坐标数 ≥8、且存在 |y|≥10000。
- 动作：每坐标追加 `[a.b万]` 显示标签（**y 保留真实值**保证柱高）+ 给 addplot 注入 `point meta=explicit symbolic`；已带 [label] 的坐标不重复缩写；单 addplot 且已缩写时再注入 `every node near coord/.append style={font=\tiny, inner sep=0pt}` 缩字号清内边距进一步防碰。缩写单位按「万」（用户确认），y 轴标签已含单位。
- 单测：135673→13.6万 / 值<10000 不缩写 / 已带 label 不重复。

**（d）回归验证**
- `mvn test` 全绿（`LatexCompilerTest` 新增 10 例：旋转 4 + 合并 2 + 缩写 3 + 保留多系列 1）。
- 重跑用例 25/27（`--compile`）：均 `compile=success`（hist 449 / hist 447），编译注入未破坏语法。
- **实测口径（bbox 检测，pdftotext 精确定位）**：27 拆柱合并后 3 个负值柱归位；25 缩写为 `13.6万` 生效，标注从 `\scriptsize` 长数字的重叠缩到 `\tiny` 后约 **1pt** 级碰触（14 根等宽柱间距的物理下限）。**1pt 为字体下限，再减需去掉「万」后缀或降数字精度（取舍，未做，待用户定夺）。**

**（f）记录**
- `docs/manual-test-cases-checklist.md` 新增 `D. 回归问题记录`，列 03/25/27/30/31 五例的现象/根因/处置。
- 新增测试资产文档 `docs/test-assets.md`（单测 / 冒烟 verify.js / 评估集 eval / 数据 / 文档一览，含运行命令与路径）。

---

### 2026-09-16 RAG 语料质量治理 + L1 模板库扩容 + 六类缺陷修复与 8 项确定性改进

**主题**：把 RAG 从「生成成功即入库、从不判定」改造成「**按判定者可靠性分级**」；并借模板库扩容的**人工视觉复核**，反向发现 6 类此前未覆盖的渲染缺陷，全部落成确定性预处理与提示词规则。全量详录见 `docs/rag-corpus-quality.md` 与 `docs/rag-templates-review/README.md`。

#### （a）RAG 语料质量治理（批次 A–C）

**问题**：索引时机在**编译之前**——`ChatService.saveGenerationHistory` 生成成功即调 `indexHistoryAsync`，而编译是用户随后手动触发的独立异步任务，两者无先后约束；准入规则只有 `ChartCodeValidator.hasDuplicateSeries` 一条。实际门槛 = 「有代码 + 多系列坐标不完全重复」，**编译成功、语法、语义、视觉全部不检查**。用模型自己的输出当范例教模型 = **闭环自举**：输出错时同样强化错误。

**改造前基线**（只读 SQL，2026-09-16）：`history` **352** 条 / `template` 7 条；T1 内容里没有 tikz **26**、T2 从未编译成功 **83**、T3 编译确认失败 **3**、T4 无数据集 **198**、T5 同一需求重复旧副本 **243**（同一句需求最多重复入库 **19 次**）。用户分布 `user4=159 / user1=83 / user16=72 / user15=32 / user11=5`。

> T5 是「RAG 为何量不出效果」的一个具体原因：`top-k-history=3`，而同一句需求最多重复入库 19 次 → **3 个名额被同一需求的副本占满，实际只召回 1 条范例**；又因 `loadHistory` 原本只有 `LIMIT` 没有 `ORDER BY`（取到**最早写入**的 N 条），很可能取到的还是最旧那条。

**设计**：把「**入库**」与「**可召回**」解耦，按判定者可靠性三级分级。

| 等级 | 判定者 | 是否召回 | 实现 |
|---|---|---|---|
| `golden` | 人工定义真值 | ✅ | 模板分区（7 条） |
| `verified` | 编译成功 **且** 静态零违例 | ✅ | `--rag-cli=verify:<userId>` 离线定级 |
| `unverified` | 无 | ❌（等价于下架，**但不删数据、完全可逆**） | 默认等级 |

仅**历史分区**按 `app.rag.min-quality`（默认 `verified`）过滤；模板分区恒为 `golden`、不受影响；**过滤后为空自动回退**并记日志，绝不把检索变哑。

- **批次 A**：新增 `ChartCodeValidator.violationsForIngest` 规则集（`NO_TIKZ` + R1/R2/R3/R5/R6/R8/R9/R12，**显式排除**已被编译前预处理兜底的 R7/R10/R11——纳入会误杀有效案例）；两条入库接入点改用规则集；**提取兜底收紧「两处都改」**（`ChatService` 三出口汇聚点 + `saveGenerationHistory` 内那条**独立的**兜底提取，只改一处不生效）；顺带修掉 `finalChartCode` 为 null 时 `chart_code_length` 的潜在 NPE。
- **批次 B**：迁移 `migrations/alter_rag_vector_quality.sql`（新增 `quality` / `data_source` 并回填）；`RagCli.purge` 改为**只读分类报告**（默认不删除任何数据），删除能力保留在 `purge:apply`。
- **批次 C**：新增 `RagQuality`（纯静态判定）；`VectorStore` 读写等级与来源，并**给 `loadHistory` / `loadAllHistory` 补 `ORDER BY vector_id DESC`**（修「只取最早 N 条」缺陷）；`Retriever` 分级过滤 + **空召回回退**；新增 `verify:<userId>` **离线定级**（纯读取+更新、**不调用大模型**、幂等：先全量重置再判定，去重保留 `vector_id` 最大的一条）；`seed` 写入置 `golden`。

**实测验证**：`mvn test` **48 → 62 → 67 全绿**；零误杀断言覆盖 R7/R10/R11 **均不命中**；`scripts/verify.cmd` **PASS=42 FAIL=0 WARN=0**（批次 A 后与最终各一次，无回退）；真实生成 history **502**（代码 442 字符、含 tikz）证明收紧**未误伤正常提取**；迁移回填 `history` unverified 352 / `template` golden 7；`verify:1` 83 条 → 跳过 6（从未编译成功）+ 6（命中准入判据）→ 合格 72 → **去重后 verified 20 条**；`rag_demo` 召回非空（`history#502` 0.83 / `#367` 0.72 / `#205` 0.71 + `template#7` 0.56 / `template#1` 0.55）；`purge` 报告 T1 26 / T5 243 / 合计 269，**未删任何数据**。

**两处与计划的偏离**：① 收紧校验**不放进** `ChartCodeExtractor` / `StructuredOutputParser`——核实后发现会连带影响 `attemptOn` 的纠正重试判定（让"你是谁"这类非图表提问也触发一次重试），故改放 `ChatService` 汇聚点；② 原计划「直接删除 T1」，实测 T5 达 243 条（占历史分区 69%），破坏性过大且删除方式未经确认 → 改为**零删除方案**。

**一处根因判断的修正**：改造前判断「闲聊被当代码」是兜底提取过松、会**持续产生**；实测修正为 **T1 的 26 条主要是历史遗留**（Java 版非 tikz 代码只有 2 条且都在 09-13 上线当天，09-14 之后无新增），**不是当前仍在持续产生的问题**。

**一次误判的排查（记录备查）**：首次跑 `verify.cmd` 出现 `PASS=39 WARN=1`（第 5 节编译链路失败），一度疑为收紧校验造成的回归；排查结论**不是回归**——`verify.js` 第 5 节取当前用户**最新一条**历史，而运行前最新的是 **499 号**（2026-09-15 23:37 产生、`generation_code` 为空，**早于本次改动**）；用真实生成补齐前置条件（history 502）后回到 PASS=42。属 `verify.js` 的**测试脆弱性**，非产品缺陷。

#### （b）L1 模板库扩容 —— 逐张目视复核推翻了 checklist 自评

**背景**：模板分区仅 7 条，冷门图型召回为空（批次 D 遗留项）。候选取 checklist「实测通过」的 `user_id=16` 466–496 区间共 14 条（去掉与 C6 部分重叠的分组柱状图）。

**做法**：`mysql -N -B -r` 从数据库**直出代码**（不经人工转写）→ `pdftoppm` 把 PDF 渲染成 PNG → **逐张目视** → 与代码逐行对照。

**结果：14 个候选全部「编译成功」，但 13 个有真实缺陷，只有 1 个（492）干净。**

| 缺陷 | 例数 | 表现 |
|---|---|---|
| ① `xbar` 坐标写成 `(分类,数值)`（应为 `(数值,分类)`） | 1（477） | pgfplots 无法把中文当 x 数值 → **数据点整批静默丢弃**，只剩空坐标轴、Y 轴分类错乱 |
| ② `fill=steelblue` —— xcolor 未定义的 CSS 风格小写色名 | 1（477） | 报 `xcolor Error` 但**填充静默回落成黑色**，编译仍"成功"、PDF 照常生成 |
| ③ 字面 `\n` 未还原（`]\ncoordinates`） | 1（490） | axis 选项在此处被截断、整张图被吞掉 → **873 字节 / 2 页全白 PDF** |
| ④ 数据来源注记写成**文件名**（`18-pctstack-expense.xlsx` 等） | 3（482/493/494） | 会教模型把内部文件名写进图表 |
| ⑤ 注记位置错 / 注记**根本不渲染** | 13 | 见下 |
| ⑥ 轴用 `·10⁵`、柱顶标注用「万」，**两套量纲记法** | 1（489） | 数值本身一致，但同一张图两套记法、观感混乱 |

**缺陷 ⑤ 的两种坏法——`axis description cs` 两种写法都是坏的**：

- 写在 `\end{axis}` **之后**（7 例：477/478/480/491/493/494/496）：坐标系已失效 → 报 `! Undefined control sequence`，节点的左下位置只是**碰巧**对；
- 写在 axis **之内**（6 例：476/481/482/489/490/495）：**不报错**，但文字被排版进 **nullfont**（日志满屏 `Missing character … in font nullfont`）→ **PDF 里能提取到文字、画面上却什么都没有**。

统一改为：位置移到 `\end{axis}` **之后**（仍在 `tikzpicture` 内），锚点换成 `(current bounding box.south west)`。

**推翻了 checklist 自评**：`docs/test/manual-test-cases-checklist.md` §B.3 把**全部 32 例**都记为「PDF 显示正常 = 是 / 通过」，其中**用例 12（空图）与用例 26（白纸）都是错的** → 该列**不可信**（未做过真正的视觉核对）。

**连带结论（重要）**：`verified`（编译成功 + 静态零违例）这个判据**同样会把上述垃圾判成合格**——477 与 490 都会被判 `verified`。故 **人工看图是唯一可靠的判定者**，这也正是把候选做成可审核物料的原因。

**处置**：14 条全部产出修正版（`hist<id>-fixed.tex/.pdf/.png`），其中 491 采用**双色方案**（两个 `\addplot` 的 `symbolic x coords` 完全相同 + 都加 `bar shift=0pt`，靠 `point meta` 的**空标签 `[]`** 抑制补 0 点的标注；因 x 有重叠，**不会被 `mergeSplitYbarAddplots` 合并**）。原版错误代码与产物**已按要求删除**，缺陷记录保留在审核包 README 中。

#### （c）8 项确定性改进落地

| # | 位置 | 改动 |
|---|---|---|
| 1 | `LatexCompiler.fixHorizontalBarCoords`（新增 P7） | 仅当同时出现 `xbar` 与 `symbolic y coords`、且某个 `coordinates` 块内**每个**点都是「第一段非数值 + 第二段数值」时整块交换；有一个不符即跳过，绝不误伤 |
| 2 | `LatexCompiler.fixUndefinedColors`（新增 P8） | 裸色名按小写查表换回规范写法（110+ 驼峰色名 + grey/gray 变体）；查不到的一律原样保留（绝不猜色） |
| 3 | `ChartCodeExtractor.normalizeEscapes` | 判据改为「`\n` 后跟的字母串**若不构成已知的、以 n 开头的 LaTeX 命令**则按换行还原」（白名单 46 个命令）；**并接入 `preprocess` 首步**——提取层只管新生成，预处理层让**历史存量记录**（如 490）同样被救回 |
| 4+5 | `LatexCompiler.normalizeSourceNote`（新增 P9） | 把所有 `\node … at (axis description cs:…)` 统一搬到 `\end{axis}` 之后、锚点换 `(current bounding box.south west)` |
| 6 | `LatexCompiler.run` | 编译后校验 PDF 体积：**< 2048 字节判为空白产物 → 返回失败**，并把原因写进编译日志（正常产物实测最小 12.7KB，空白产物 873 字节） |
| 7 | `PromptTemplates` | 新增 **R13–R16** + 4 条反例 + 自检 **18–20** 条；`VERSION` → `v1.5-coord-color-note` |
| 8 | 流程约定 | 本地复核模板时**同时跑真实 `preprocess`**（用 `target/classes` + 单文件 Java 程序直接调用），既看「裸代码是否已正确」，也看「生产会不会改坏」 |

> `preprocess` 链同时改为**顺序写法**（逐步赋值），替代原来的深层嵌套调用，便于阅读与增删步骤。

**顺带修正一处原有的错误指导（重要）**：提示词**原自检第 6 条**要求「数据来源注记在 `\end{axis}` **之前**」——而那**正是导致注记不可见的写法**（文字进 nullfont）。这很可能就是那 6 例注记消失的根源。已改为「写在 `\end{axis}` 之后、用 `(current bounding box.south west)` 定位，未使用 `axis description cs`」；`【反例】`中「把数据来源等文字写在 axis 外」一条同步改为「写在 `\end{tikzpicture}` 之后（跑出图外）」。

#### （d）验证

- `mvn test`：**67 → 79 例全绿**（新增 12 例覆盖每个新步骤的正反例：`ChartCodeExtractorTest` +4、`LatexCompilerTest` +8）
- **端到端**：把数据库里两个出错的原版代码取出，**只过一遍新 `preprocess`** 再编译——

| 案例 | 修复前 | 过 `preprocess` 后 | 人工修正版（对照） |
|---|---|---|---|
| hist477 | 空坐标轴，25.8KB | **29419 字节，8 根蓝柱全对、注记可见、编译零错误** | 29410 字节 |
| hist490 | **873 字节 / 2 页白纸** | **32815 字节，三条折线全对、注记可见、编译零错误** | 32808 字节 |

→ 这两个缺陷已**不需要人工介入**，新预处理会自动修好。

#### （e）本次新增 / 更新的文档

- **新增** `docs/rag-corpus-quality.md`：语料治理记录（问题 / 基线 / 分级设计 / 逐批改动与偏离 / 实跑验证 / **6 项剩余盲区** / 复现命令 / 一键回退方式）
- **新增** `docs/rag-l1-candidates.md`：模板库扩容候选清单（14 条 + 未纳入理由 + 可信度依据修正）
- **新增** `docs/rag-templates-review/`：**45 个文件**——14 组入库模板 `hist<id>-fixed.tex / .pdf / .png` + `header.tex` / `footer.tex`（编译外壳）+ `README.md`（核验方法 / 逐条判定 / 六类缺陷 / 修正表 / 8 项落地明细 / 端到端验证 / 需求原文）
- **更新** `docs/manual-test-cases-checklist.md` 相关口径说明；`cv-java` 简历素材同步至 **R1–R16 / 自检 20 条 / 反例 15 条 / `v1.5-coord-color-note`**，并修正多处行号引用

#### （f）剩余盲区（**必须如实理解，不要高估**）

1. **`verified` 仍有盲区**：「编译成功 + 静态零违例」**不等于**语义/视觉正确——本次 477（空图）与 490（白纸）都会被判 `verified`；其中 **R4（数值与坐标轴量纲一致）与视觉重叠没有自动判据**，只能人工抽检。
2. 项目**没有用户级单次生成质量信号**：`feedback` 表是功能建议 / 界面 / bug 类通用反馈，**无 history 关联、无评分**。
3. **示意数据不可自动识别**：`data_source` 只能标 `dataset` / `no-dataset`，「使用公开统计」与「模型自拟示意数据」都不带数据集、无法区分。
4. 「编译成功」用 `generation_path` 非空判定，而 `COMPILE_ERROR` 同时覆盖 LaTeX 报错 / xelatex 执行异常 / 任务中断，**环境类失败也会被保守判为"未验证"**（方向安全，但会少收录）。
5. **只对账号 1 做了离线定级**，其余账号 4/15/16 共 **332 条仍为 `unverified`**、不参与召回。
6. **提示词 / 检索消融实验仍延后**：在语料分级完成前做该实验，结论会被脏语料污染，故先治理后测量。

**回退方式**：把 `app.rag.min-quality` 设为 `unverified`（或环境变量 `RAG_MIN_QUALITY=unverified`）即等价于「不过滤」，行为回到改造前；`verify` 可重复执行、可随时重算。

---

### 2026-09-16 编译提交延迟复测（G1 异步契约）与并发触发条件更正

**目的**：批次2 验收的「4 任务并发提交 POST 响应 14–19ms」（见上文「批次2 落地 §5」，2026-09-14）只有手测记录、**没有脚本固化**，无法重复执行。本次补一个脚本把该测量变成一条命令，并复测一次。

**新增脚本**：`spring-backend/measure-submit-latency.mjs`（Node，零第三方依赖，与 `verify.js` 同风格）

```bash
# 在 spring-backend 目录（后端已启动；账号需名下有 ≥ --count 条带代码的历史）
node measure-submit-latency.mjs --email=<user@example.com> --password=<pwd> --count=4 --rounds=3
# 可选：--base=http://localhost:3000 / --timeout=10000 / --no-wait（不等任务终态）
```

**实测（2026-09-16 20:47，本机 Windows，账号 user_id=17，历史 #565–#568）**：

| 项 | 结果 |
|---|---|
| 提交样本 | 4 并发 × 3 轮 = **12 次** `POST /api/compile/{id}` |
| 提交耗时 | min **7ms** / median **12ms** / p95 **24ms** / max **24ms**（单轮墙钟 11–24ms） |
| 任务终态 | **12/12 全部 success**，`duration_ms` 2280–6332ms（首轮 2 个任务偏慢，为 6.3s；其余约 2.3–2.5s） |
| 对照 | 改造前同步等待编译，最长 30s（`LATEX_TIMEOUT_MS=30000`） |

**结论**：与 2026-09-14 手测的 14–19ms 同量级；该数字**现在可一键复现**（脚本 + 上面的命令），不必再引用旧手测记录。

#### 更正：原并发缺陷的「触发路径」写错了

上文「批次2 落地 §8」写过「前端双击「生成PDF」即可触发」——**这句不成立**，本次核实后更正：

- `src/views/ChartGenerator.vue:145` 的「生成PDF」按钮绑的是 **`compiling` 这一个全局 ref**（定义在 `:473`），而 Element Plus 按钮在 `loading` 时会被禁用并吞掉点击：`node_modules/element-plus/es/components/button/src/use-button.mjs:47`（`disabled: _disabled.value || props.loading`）与 `:66-69`（`handleClick` 在 disabled/loading 时提前 return）。→ **一个编译在跑时，同页所有消息的「生成PDF」按钮全部点不动**，单标签页正常操作**不会**产生第二个并发编译。
- 真实触发路径 = **同一账号的多个并发请求**：多标签页 / 多设备 / 直接调接口 / 脚本（本次复测就用脚本一次性提交了 12 个任务）。
- 修复后的代码在 `CompileService.java:77-79` 用 `Files.createTempDirectory` 原子创建唯一目录，「同毫秒进入编译」已不再有害；但**该窗口本身确实可达**：本次 12 次提交形成 6 组两两并行编译，其中 **20:47:50.705 一组两个任务在同一毫秒拿到编译许可**（日志两行「获得编译许可，开始编译 … 剩余信号量=0」时间戳相同），约占 1/6。
- 口径不变：修复前那个 500 **未能稳定复现**（见上文 §8 的诚实口径）；现在能说的是「窗口可达 + 修复后并发全绿」，**不是**「已复现原 bug」。
