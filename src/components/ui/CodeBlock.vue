<template>
  <div class="code-block" :class="{ 'code-block--bordered': bordered }">
    <div class="code-block__bar">
      <span class="code-block__lang">{{ langLabel }}</span>
      <button
        v-if="showCopy"
        class="code-block__copy"
        type="button"
        @click="copy"
      >
        <el-icon v-if="!copied"><CopyDocument /></el-icon>
        <el-icon v-else class="is-done"><CircleCheck /></el-icon>
        <span>{{ copied ? '已复制' : '复制' }}</span>
      </button>
    </div>
    <div class="code-block__body" :style="bodyStyle">
      <div
        v-for="(line, i) in lines"
        :key="i"
        class="code-block__line"
      >
        <span class="code-block__ln">{{ i + 1 }}</span>
        <code class="code-block__code" v-html="line || ' '" />
      </div>
    </div>
  </div>
</template>

<script>
import { CopyDocument, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

export default {
  name: 'CodeBlock',
  components: { CopyDocument, CircleCheck },
  props: {
    code: { type: String, default: '' },
    language: { type: String, default: 'latex' },
    maxHeight: { type: String, default: '320px' },
    showCopy: { type: Boolean, default: true },
    bordered: { type: Boolean, default: true }
  },
  data() {
    return { copied: false }
  },
  computed: {
    langLabel() {
      const map = { latex: 'LaTeX', tex: 'TeX', json: 'JSON', text: 'TEXT' }
      return map[this.language] || this.language.toUpperCase()
    },
    bodyStyle() {
      return this.maxHeight ? { maxHeight: this.maxHeight } : {}
    },
    lines() {
      return this.highlight(this.code || '').split('\n')
    }
  },
  methods: {
    escapeHtml(s) {
      return s
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
    },
    // 轻量 LaTeX 词法高亮（仅用品牌蓝 + 灰，契合白底蓝主题，无第三方依赖）
    highlight(code) {
      return code
        .split('\n')
        .map((rawLine) => {
          const idx = rawLine.indexOf('%')
          let codePart = rawLine
          let commentPart = ''
          if (idx >= 0) {
            codePart = rawLine.slice(0, idx)
            commentPart = rawLine.slice(idx)
          }
          let html = this.escapeHtml(codePart)
          html = html.replace(/(\\[a-zA-Z]+|\\.)/g, '<span class="tk-cmd">$1</span>')
          html = html.replace(/([{}])/g, '<span class="tk-brace">$1</span>')
          html = html.replace(/\b(\d+(?:\.\d+)?)\b/g, '<span class="tk-num">$1</span>')
          if (commentPart) {
            html += '<span class="tk-comment">' + this.escapeHtml(commentPart) + '</span>'
          }
          return html
        })
        .join('\n')
    },
    async copy() {
      const text = this.code || ''
      try {
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(text)
        } else {
          const ta = document.createElement('textarea')
          ta.value = text
          ta.style.position = 'fixed'
          ta.style.left = '-9999px'
          document.body.appendChild(ta)
          ta.select()
          document.execCommand('copy')
          document.body.removeChild(ta)
        }
        this.copied = true
        ElMessage.success('已复制到剪贴板')
        setTimeout(() => { this.copied = false }, 1500)
      } catch (e) {
        ElMessage.error('复制失败，请手动选择')
      }
    }
  }
}
</script>

<style scoped>
.code-block {
  background: var(--bg-soft, #F7F8FA);
  border-radius: var(--radius-sm, 8px);
  overflow: hidden;
  font-size: 13px;
}
.code-block--bordered {
  border: 1px solid var(--border, #E5E7EB);
}
.code-block__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: var(--brand-soft, #EEF1FF);
  border-bottom: 1px solid var(--border, #E5E7EB);
}
.code-block__lang {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: .04em;
  color: var(--brand, #4D6BFE);
  text-transform: uppercase;
}
.code-block__copy {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  font-size: 12px;
  color: var(--brand, #4D6BFE);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: background .15s ease;
}
.code-block__copy:hover {
  background: rgba(77, 107, 254, 0.12);
}
.code-block__copy .is-done {
  color: var(--success, #67C23A);
}
.code-block__body {
  overflow: auto;
  padding: 10px 0;
  background: var(--bg-surface);
  font-family: var(--font-mono);
  line-height: 1.6;
  color: var(--text-regular, #41464C);
}
.code-block__line {
  display: flex;
  padding: 0 12px;
  white-space: pre;
}
.code-block__line:hover {
  background: rgba(77, 107, 254, 0.05);
}
.code-block__ln {
  flex: 0 0 auto;
  width: 34px;
  margin-right: 12px;
  text-align: right;
  color: var(--text-muted, #8A8F99);
  user-select: none;
}
.code-block__code {
  flex: 1 1 auto;
  white-space: pre;
}
/* 单色蓝语法：命令=品牌蓝，数字=深蓝，括号=灰，注释=浅灰 */
.tk-cmd { color: var(--brand, #4D6BFE); font-weight: 600; }
.tk-num { color: var(--brand-active, #2E46C4); }
.tk-brace { color: var(--text-muted, #8A8F99); }
.tk-comment { color: var(--text-muted, #8A8F99); font-style: italic; }
</style>
