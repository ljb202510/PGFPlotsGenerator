# 开发日志 — PGFPlotsGenerator
> 本日志为历史记录，权威技术文档以 README.md 为准
> 项目：PGFPlotsGenerator（前端 Vue3 + 后端 Node/Express + MySQL）
> 记录区间：2025-12-03 ～ 2026-09-09

## 目录
- 一、前期已完成项
- 二、日常开发记录（按时间）
- 三、会议汇报
- 四、老师建议
- 五、待定 / 废除项 / 疑问
- 六、部署相关
- 七、技术说明与参考

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
5. 对话界面拖拽上传，会显示“上传失败: {"code":400,"message":"数据集名称和描述不能为空"}”，名称和描述默认为上传的文件名
5. 每次对话之后，应当清除选中文件。
2. 数据上传的“修改”按钮应为编辑按钮（默认显示当前名称和描述，支持修改）
5. 当点击“历史记录”时，后端终端报错：从数据库获取历史记录失败: Error: You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near '?' at line 20
5. 文件大小限制 10000 字节，太小了

---

1. 下载 LaTeX 本地环境，不然无法编译
2. API 调用错误的情况，插入 callid 逻辑还没修改
3. 前后端编译对接问题（历史记录+图表生成界面两个接口）
2. 图表类型的完善？应该有什么类型，删掉吧，类型太多了（数据库和前后端代码都要删除）
`<!-- PDF预览对话框 -->` 预览框的关闭（X 图标），无法关闭整个框（会留下这一行）
1.5 图表生成和历史记录生成的 pdf 应当一致，统一显示
5. API 调用错误: 发送 `.xlsx` 文件，报错，之后都不能调用 AI。
3. 在图表生成界面一旦点击其他组件/页面，就会清除所有记录，我不希望这样，我希望只有退出登录或者刷新界面（再添加一个按钮，“新建对话”按下按钮时）才会清除当前所有对话记录
2. 发出消息如果带有文件，需要在页面上显示出来，而不是只显示文字部分的描述

---

7. 拖拽的区域可以参考 deepseek 而不是固定在右端，底层可以有一个上传历史文件的选项，弹出原来右边“历史文件”的部分，“点击上传”的按钮也需要添加在底部，对话界面可占据全部页面
1. 上传历史文件界面不友好（显示不好看）

### 周二 12.16

- 删除描述中的“用户查询”删除字段
- 删除重新生成选项
3. 上传历史文件里的“取消”和“确认选择”删除这两个按钮相关代码

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
现在普通用户和管理员登录成功之后都会看到一闪而过的“登录界面”（包含邮箱 密码输入框，但其他导航栏正常显示），我已经改了很久了还是没解决。请帮我仔细检查。给出几种解决方案

0. 渲染逻辑冲突：App.vue 的模板根据认证状态条件渲染，而路由守卫也进行重定向。如果冲突的话只取其中一种渲染逻辑吧。
1. 删除了路由守卫 `index.js`，但还是不行结果一样
2. 猜想，当时为了解决新开标签页（跳转反馈管理）会弹出“请先登录”的认证问题添加的 `created` 和 `watch`（使用 `checkAuthStatus` 多了，原来只在 `mounted` 使用，现在 `created` 和 `watch` 都使用了）
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
帮我正确完成后端反馈接口的调用，反馈通知不计入未读消息数（没有已读状态未读状态，不用考虑），notice 表是系统通知表和反馈表不一样，在“我的通知”页面，单独从 feedback 表中获取当前用户被回复的反馈，作为反馈通知展示。

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

4. 编辑功能删除，优化前端界面。很多不必要的“对话框”等等
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

5. 管理员界面在“提交时间” “发布时间” “注册时间” 右边增加箭头，点击可按时间正序/逆序排序。

---

1. “提交时间”的箭头样式需要调整
2. 用户界面 上传数据 “上传时间” 右边增加箭头，点击可按时间正序/逆序排序。
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
   失败，定位问题应该就是“缺少 psutil 模块和 Python 的 SSL 模块”
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
添加了“登录”“调用”“编译”成功和失败，插入系统日志。

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

#### 在提示词里添加“渲染规则”之后出现了很多bug
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
- 停止生成：发送按钮生成期变为“停止”方块；前端 AbortController 取消请求，后端 `chat.js` 以 `res.on('close')` 联动中止 Qwen/DeepSeek 上游调用，避免额度浪费与多余落库；生成期防并发发送，组件卸载自动中止。

#### 2. 错误提示体验
- 全局 ElMessage 默认 `duration: 5000ms` + `showClose: true`（可点击关闭，App.vue `el-config-provider`）。
- 编译失败 Toast 不再截断 300 字符，完整展示后端信息。
- 编译失败样本与日志自动留档 `backend/storage/debug/hist{id}_{时间戳}.tex(.log)`，控制台仅提示文件位置。

#### 3. 界面问题修复
- 首屏“快速开始模版”遮挡 AI 助手初始消息并阻断拖拽 → `guide-area` 移入 `.chat-messages` 容器。

#### 4. AI 生成/编译链路修复（双端防御 + 提示词 PE 重构）
- AI 输出完整文档（`\documentclass`/`\usepackage`/`\begin{document}`…）被再包裹 → 提示词禁止输出文档脚手架；`preprocessLatexCode` 无条件清理；编译外壳预置 `pgf-pie`。
- 数值标注旧无效键 `nodes near coords style=` → 改正确键 `every node near coord/.append style={font=\scriptsize, fill=none, draw=none, inner sep=1pt, anchor=south}`，并强制“有 mark 必配标注”（R3）。
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
- [ ] 还有一个bug在生成的时候前端点击切换模型，也会切换提示，正在使用 Deepseek-V4-Flash 生成图表代码。实际使用模型不变。可以“你的模型是什么”来检验/后端调用也会显示模型。（不用解决）
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
