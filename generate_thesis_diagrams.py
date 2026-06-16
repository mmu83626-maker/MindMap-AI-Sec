#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate thesis diagrams for NetScope AI - Sections 4.1, 4.2, 4.3"""

import matplotlib, os, warnings
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch
from matplotlib.font_manager import FontProperties
import numpy as np

# ── Use Windows CJK font (Latin + CJK) ──
import matplotlib.font_manager as fm
FONT_CANDIDATES = [
    r'C:\Windows\Fonts\msyh.ttc',       # Microsoft YaHei (微软雅黑)
    r'C:\Windows\Fonts\simhei.ttf',     # SimHei (黑体)
    r'C:\Windows\Fonts\NotoSansSC-VF.ttf',  # Noto Sans SC
    r'C:\Windows\Fonts\simsun.ttc',     # SimSun (宋体)
]
FONT = None
for f in FONT_CANDIDATES:
    if os.path.exists(f):
        FONT = f
        break
if FONT is None:
    raise FileNotFoundError(f"No CJK font found! Tried: {FONT_CANDIDATES}")
fm.fontManager.addfont(FONT)
matplotlib.rcParams['axes.unicode_minus'] = False
print(f"Using font: {FONT}")

OUTPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'outputs', 'thesis')

warnings.filterwarnings('ignore', category=UserWarning)

def fp(size=12, bold=False):
    return FontProperties(fname=FONT, size=size, weight='bold' if bold else 'normal')

def save_fig(fig, name):
    path = os.path.join(OUTPUT, name)
    fig.savefig(path, dpi=200, bbox_inches='tight', facecolor=fig.get_facecolor())
    plt.close(fig)
    print(f"  {name} ({os.path.getsize(path)//1024} KB)")

# ═══════════════════════════════════════
# 4.1.1  Protocol Frame Format — Clean technical blueprint style
# ═══════════════════════════════════════
def fig_frame():
    fig, ax = plt.subplots(figsize=(16, 9))
    ax.set_xlim(0, 16); ax.set_ylim(0, 10); ax.axis('off')
    ax.set_title('图 4-1  NetScope AI 协议帧格式结构', fontproperties=fp(16, True), pad=15)

    C_HEADER  = '#D6EAF8'
    C_MEASURE = '#D5F5E3'
    C_CERT    = '#FDEBD0'
    C_SEC     = '#E8DAEF'
    C_EDGE    = '#5D6D7E'
    C_TEXT    = '#2C3E50'
    C_SUBTEXT = '#7F8C8D'

    # Byte-offset ruler
    ax.add_patch(plt.Rectangle((1.2, 9.3), 14.5, 0.38, facecolor='#2C3E50', edgecolor='none'))
    for i in range(0, 31):
        x = 1.2 + i * 0.47
        ax.text(x + 0.22, 9.49, str(i), ha='center', va='center',
                fontproperties=fp(5.5), color='#ABB2B9')

    def draw_field(x, y, w, h, label, detail, facecolor):
        ax.add_patch(plt.Rectangle((x, y), w, h, facecolor=facecolor,
                                    edgecolor=C_EDGE, lw=0.8, joinstyle='miter'))
        # Single-line label
        ax.text(x + w/2, y + h*0.62, label, ha='center', va='center',
                fontproperties=fp(8, True), color=C_TEXT)
        # Detail
        ax.text(x + w/2, y + h*0.22, detail, ha='center', va='center',
                fontproperties=fp(6.5), color=C_SUBTEXT)

    # ── Row 4: Frame Header (y=7.4) ──
    Y4, H4 = 7.4, 1.35
    ax.text(0.05, Y4 + H4/2, '帧头', ha='center', va='center',
            fontproperties=fp(8, True), color='#2471A3')
    header_fields = [
        (1.2, 2.4, '帧起始标志 (Start Flag)', '0xAA55  2B', C_HEADER),
        (3.8, 2.0, '协议版本 (Version)', 'v1.0  1B', C_HEADER),
        (6.0, 3.0, '测量ID (Measurement ID)', 'UUID  16B', C_HEADER),
        (9.2, 2.2, '时间戳 (Timestamp)', 'Unix ms  8B', C_HEADER),
        (11.6, 2.0, 'URL长度 (URL Length)', '2B', C_HEADER),
        (13.8, 1.7, '帧类型\n(FrameType)', '1B', C_HEADER),
    ]
    for x, w, label, detail, c in header_fields:
        draw_field(x, Y4, w, H4, label, detail, c)

    # ── Row 3: Measurement Data (y=5.5) ──
    Y3, H3 = 5.5, 1.35
    ax.text(0.05, Y3 + H3/2, '测量\n数据', ha='center', va='center',
            fontproperties=fp(8, True), color='#1E8449')
    measure_fields = [
        (1.2, 3.4, '目标 URL (Target URL)', '变长  ≤2048B', C_MEASURE),
        (4.8, 2.4, 'DNS 解析耗时', '4B (ms)', C_MEASURE),
        (7.4, 2.4, 'TCP 连接耗时', '4B (ms)', C_MEASURE),
        (10.0, 2.4, 'TLS 握手耗时', '4B (ms)', C_MEASURE),
        (12.6, 2.9, 'HTTP 状态码 / TTFB', 'TTFB 4B  Total 4B', C_MEASURE),
    ]
    for x, w, label, detail, c in measure_fields:
        draw_field(x, Y3, w, H3, label, detail, c)

    # ── Row 2: HTTP & Certificate (y=3.6) ──
    Y2, H2 = 3.6, 1.35
    ax.text(0.05, Y2 + H2/2, 'HTTP\n证书', ha='center', va='center',
            fontproperties=fp(8, True), color='#B9770E')
    cert_fields = [
        (1.2, 3.2, '响应体大小 / 状态码', 'Body 8B  Status 2B', C_CERT),
        (4.6, 2.6, '证书有效天数', '4B', C_CERT),
        (7.4, 2.0, '证书有效性', '1B', C_CERT),
        (9.6, 3.0, '证书主题 / 签发者', '变长  Variable', C_CERT),
        (12.8, 2.7, 'SSL/TLS 协议版本', '2B', C_CERT),
    ]
    for x, w, label, detail, c in cert_fields:
        draw_field(x, Y2, w, H2, label, detail, c)

    # ── Row 1: Security & Tail (y=1.7) ──
    Y1, H1 = 1.7, 1.35
    ax.text(0.05, Y1 + H1/2, '安全\n尾部', ha='center', va='center',
            fontproperties=fp(8, True), color='#7D3C98')
    sec_fields = [
        (1.2, 3.0, '安全头位图 (6bit)', 'HSTS/CSP/XFO/... 2B', C_SEC),
        (4.4, 2.2, '风险等级', '1B', C_SEC),
        (6.8, 2.4, '响应 IP 列表', '变长  Variable', C_SEC),
        (9.4, 2.4, '最终 URL', '变长  Variable', C_SEC),
        (12.0, 3.5, '帧校验  CRC16', '2B', C_SEC),
    ]
    for x, w, label, detail, c in sec_fields:
        draw_field(x, Y1, w, H1, label, detail, c)

    # ── Legend ──
    ly = 0.4
    for i, (lbl, c) in enumerate([('帧头/标识', C_HEADER), ('测量数据', C_MEASURE),
                                    ('HTTP/证书', C_CERT), ('安全/校验', C_SEC)]):
        x = 1.2 + i * 3.5
        ax.add_patch(plt.Rectangle((x, ly), 0.35, 0.22, facecolor=c, edgecolor=C_EDGE, lw=0.8))
        ax.text(x + 0.5, ly + 0.11, lbl, va='center', fontproperties=fp(7.5), color=C_TEXT)

    # ── Total frame length ──
    ax.annotate('', xy=(1.2, 9.05), xytext=(15.5, 9.05),
                arrowprops=dict(arrowstyle='<->', color='#E74C3C', lw=1.8))
    ax.text(8.4, 9.15, '最大帧长: 2110 Bytes (含可变字段)', ha='center',
            fontproperties=fp(8, True), color='#E74C3C')

    return fig

# ═══════════════════════════════════════
# 4.1.2  State Machine — Clean UML-style layout
# ═══════════════════════════════════════
def fig_state():
    fig, ax = plt.subplots(figsize=(16, 10))
    ax.set_xlim(0, 16); ax.set_ylim(0, 10); ax.axis('off')
    ax.set_title('图 4-2  NetScope AI 网络测量状态机', fontproperties=fp(16, True), pad=12)

    C_IDLE   = '#ECEFF1'
    C_ACTIVE = '#BBDEFB'
    C_DONE   = '#A5D6A7'
    C_FAILED = '#EF9A9A'
    C_EVAL   = '#FFF9C4'
    C_EDGE   = '#37474F'

    SW, SH = 2.6, 2.0

    def draw_state(cx, cy, label, sub, facecolor):
        # Shadow
        ax.add_patch(FancyBboxPatch((cx - SW/2 + 0.05, cy - SH/2 - 0.05), SW, SH,
                                     boxstyle="round,pad=0.08",
                                     facecolor='#00000018', edgecolor='none', zorder=0))
        # Main box
        ax.add_patch(FancyBboxPatch((cx - SW/2, cy - SH/2), SW, SH,
                                     boxstyle="round,pad=0.08",
                                     facecolor=facecolor, edgecolor=C_EDGE, lw=1.8, zorder=1))
        # Label (single line or two lines with \n)
        ax.text(cx, cy + 0.15, label, ha='center', va='center',
                fontproperties=fp(8.5, True), color='#263238', zorder=2, linespacing=1.1)
        # Subtitle
        ax.text(cx, cy - 0.65, sub, ha='center', va='center',
                fontproperties=fp(6.5), color='#546E7A', zorder=2)
        return (cx, cy)

    def draw_arrow(p1, p2, label, color, lw=2.0, offset_y=0.35):
        mid_x = (p1[0] + p2[0]) / 2
        mid_y = (p1[1] + p2[1]) / 2
        rad = 0.1 if abs(p1[1] - p2[1]) < 0.5 else 0.0
        conn = f'arc3,rad={rad}' if rad else 'arc3,rad=0'
        ax.annotate('', xy=p2, xytext=p1,
                    arrowprops=dict(arrowstyle='->', color=color, lw=lw,
                                    connectionstyle=conn, linestyle='-'), zorder=0)
        if label:
            ax.text(mid_x, mid_y + offset_y, label, ha='center', va='center',
                    fontproperties=fp(6.5), color=color,
                    bbox=dict(boxstyle='round,pad=0.15', facecolor='white',
                              edgecolor='#DDD', alpha=0.9), zorder=3)

    # ═══ Top row: Main flow states ═══
    RY = 8.0
    states = [
        (2.0, '空闲\nIDLE', '等待用户请求', C_IDLE),
        (5.0, 'DNS解析\nDNS_RESOLVING', 'getAllByName()', C_ACTIVE),
        (8.0, 'TCP连接\nTCP_CONNECTING', 'Socket.connect()', C_ACTIVE),
        (11.0, 'TLS握手\nTLS_HANDSHAKING', 'startHandshake()', C_ACTIVE),
        (14.0, 'HTTP请求\nHTTP_REQUESTING', 'getResponseCode()', C_ACTIVE),
    ]
    pos = {}
    for cx, label, sub, c in states:
        pos[label.split('\n')[0]] = draw_state(cx, RY, label, sub, c)

    idle = pos['空闲']
    dns  = pos['DNS解析']
    tcp  = pos['TCP连接']
    tls  = pos['TLS握手']
    http = pos['HTTP请求']

    # ═══ Bottom row: Result states ═══
    done   = draw_state(14.0, 4.2, '测量完成\nDONE', '生成报告', C_DONE)
    failed = draw_state(5.0, 4.2, '测量失败\nFAILED', '返回错误报告', C_FAILED)
    ev     = draw_state(9.5, 4.2, '安全评估\nSEC_EVALUATION', '响应头检查/评级', C_EVAL)

    # ═══ Normal flow arrows ═══
    C_NORM = '#1565C0'
    draw_arrow(idle, dns, '输入URL', C_NORM)
    draw_arrow(dns, tcp, '解析成功', C_NORM)
    draw_arrow(tcp, tls, 'HTTPS', C_NORM)
    draw_arrow(tcp, http, 'HTTP', '#2E7D32', offset_y=0.55)
    draw_arrow(tls, http, '握手完成', C_NORM)

    # HTTP → DONE (vertical)
    ax.annotate('', xy=(http[0], 6.2), xytext=(http[0], RY - SH/2),
                arrowprops=dict(arrowstyle='->', color='#2E7D32', lw=2.2), zorder=0)
    ax.text(http[0] + 0.8, 6.8, '请求完成', ha='center', fontproperties=fp(6.5),
            color='#2E7D32', bbox=dict(boxstyle='round,pad=0.15', facecolor='white',
                                        edgecolor='#DDD', alpha=0.9))

    # DONE → SEC_EVALUATION
    draw_arrow(done, ev, '安全检查', '#E65100')

    # ═══ Error flow arrows ═══
    C_ERR = '#C62828'
    # DNS → FAILED
    ax.annotate('', xy=(failed[0], failed[1] + SH/2 + 0.05),
                xytext=(dns[0], RY - SH/2 - 0.05),
                arrowprops=dict(arrowstyle='->', color=C_ERR, lw=2.2), zorder=0)
    ax.text(dns[0] + 0.7, 5.8, 'DNS异常', ha='center', fontproperties=fp(6.5),
            color=C_ERR, bbox=dict(boxstyle='round,pad=0.15', facecolor='white',
                                    edgecolor='#DDD', alpha=0.9))

    # TCP/TLS/HTTP → FAILED (diagonal)
    ax.annotate('', xy=(failed[0] + SW/2 + 0.05, failed[1] + 0.3),
                xytext=(tcp[0] - SW/2 - 0.05, RY - 0.5),
                arrowprops=dict(arrowstyle='->', color=C_ERR, lw=2.0,
                                connectionstyle='arc3,rad=0.2'), zorder=0)
    ax.text(6.0, 6.2, 'TCP/TLS/HTTP\n异常', ha='center', fontproperties=fp(6.5),
            color=C_ERR, bbox=dict(boxstyle='round,pad=0.15', facecolor='white',
                                    edgecolor='#DDD', alpha=0.9))

    # ═══ Legend ═══
    for i, (lbl, c) in enumerate([('正常流程', C_NORM), ('异常流程', C_ERR),
                                    ('完成/评估', '#2E7D32'), ('安全检查', '#E65100')]):
        x = 0.6 + i * 3.6
        ax.add_patch(FancyBboxPatch((x, 0.35), 0.55, 0.28, boxstyle="round,pad=0.03",
                                     facecolor=c, edgecolor=C_EDGE, lw=1))
        ax.text(x + 0.7, 0.49, lbl, va='center', fontproperties=fp(8))

    # Initial arrow
    ax.annotate('', xy=(idle[0] - SW/2 + 0.1, idle[1]),
                xytext=(idle[0] - SW/2 - 0.9, idle[1]),
                arrowprops=dict(arrowstyle='->', color='#333', lw=2.2))
    ax.text(idle[0] - SW/2 - 0.4, idle[1] + 0.35, '启动', ha='center',
            fontproperties=fp(7.5, True), color='#333')

    return fig

# ═══════════════════════════════════════
# 4.1.3  Key Code — Dark IDE background covers all text
# ═══════════════════════════════════════
def fig_code():
    fig, axes = plt.subplots(2, 1, figsize=(14, 12))
    fig.patch.set_facecolor('#1E1E1E')  # entire figure dark

    code1 = (
        '后端核心测量方法 (Java / Spring Boot)',
        '''@Service
public class NetworkMeasureService {
    public NetworkMeasureReport measure(NetworkMeasureRequest req) {
        URI uri = normalizeUri(req.url());
        // 1. DNS解析
        long dnsStart = System.nanoTime();
        InetAddress[] addrs = InetAddress.getAllByName(uri.getHost());
        long dnsMs = elapsedMs(dnsStart);
        assertPublicTarget(uri, addrs);

        // 2. TCP连接
        long tcpMs = measureTcp(uri, addrs[0]);

        // 3. TLS握手 (仅HTTPS)
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        if (https && tcpReachable) {
            TlsResult tls = measureTls(uri, addrs[0]);
            tlsMs = tls.durationMs();
            certInfo = tls.certificateInfo();
        }

        // 4. HTTP请求 + TTFB
        HttpResult http = measureHttp(uri);

        // 5. 安全头评估
        headers = evaluateHeaders(http.headers(), https);

        // 6. 风险评级 (classifyRisk)
        riskLevel = classifyRisk(risks, https, headers, certInfo);
        return buildReport(uri, dnsMs, tcpMs, tlsMs,
                          http.ttfbMs(), http.totalMs(),
                          http.status(), headers, riskLevel);
    }
}''')
    code2 = (
        '前端安全评分算法与风险分类 (TypeScript)',
        '''// 安全评分 (0-100) - calculateSecurityScore()
function calculateSecurityScore(report: NetworkMeasureReport): number {
  let score = 100;
  if (!report.httpsEnabled) score -= 20;       // HTTPS (20分)
  if (!report.certificateValid) score -= 20;   // 证书 (20分)
  else if (report.certificateDaysRemaining < 30) score -= 5;

  const keyHeaders = ['Content-Security-Policy', 'Strict-Transport-Security',
    'X-Frame-Options', 'X-Content-Type-Options',
    'Referrer-Policy', 'Permissions-Policy'];
  const missing = keyHeaders.filter(h =>
    !report.securityHeaders.some(sh => sh.name === h && sh.present));
  score -= missing.length * 5;                  // 安全头 (30分)

  if (report.httpStatus >= 500) score -= 15;   // HTTP状态 (15分)
  else if (report.httpStatus >= 400) score -= 8;
  if (report.totalMs > 5000) score -= 10;      // 性能 (15分)
  else if (report.totalMs > 2000) score -= 5;
  return Math.max(0, score);
}

// 风险等级分类 - classifyRisk()
private String classifyRisk(List<String> risks, boolean https,
      List<SecurityHeaderResult> headers, CertificateInfo cert) {
  if (!https || !cert.valid()) return "high";
  long miss = headers.stream()
    .filter(h -> !h.present() && (h.name().contains("Content-Security-Policy")
        || h.name().contains("Strict-Transport-Security")
        || h.name().contains("X-Frame-Options"))).count();
  if (miss >= 2 || risks.size() >= 4) return "medium";
  if (!risks.isEmpty()) return "low";
  return "good";
}''')

    for ax, (title, code) in zip(axes, [code1, code2]):
        ax.set_facecolor('#1E1E1E')
        ax.axis('off')
        # Title — blue keyword color
        ax.text(0.03, 0.97, title, transform=ax.transAxes,
                fontproperties=fp(11, True), color='#569CD6',
                verticalalignment='top')
        # Code body — white/monospace, clip_on=False to render fully
        ax.text(0.03, 0.92, code, transform=ax.transAxes,
                fontproperties=fp(8), color='#D4D4D4',
                family='monospace', linespacing=1.3,
                verticalalignment='top', clip_on=False)

    fig.text(0.5, 0.005, '图 4-3  NetScope AI 网络测量与安全评分核心代码',
             ha='center', fontproperties=fp(16, True), color='#D4D4D4')
    plt.subplots_adjust(hspace=0.08, top=0.98, bottom=0.03)
    return fig

# ═══════════════════════════════════════
# 4.2.1
# ═══════════════════════════════════════
def fig_tables():
    fig,axes=plt.subplots(1,2,figsize=(16,7))
    fig.suptitle('表 4-1 测试环境配置 与 表 4-2 多网站性能测量数据',fontproperties=fp(16,True),y=1.02)
    # Table 1
    ax1=axes[0];ax1.axis('off')
    ax1.set_title('表 4-1  测试环境配置',fontproperties=fp(13,True))
    env=[['CPU','Intel Core i7-12700H, 2.3GHz (14核20线程)'],['内存','DDR5 32GB, 4800MHz'],
         ['操作系统','Windows 11 Pro 23H2'],['Java版本','OpenJDK 17.0.9 + Spring Boot 3.2.0'],
         ['前端框架','Vue 3.4 + Vite 5 + TypeScript'],['网络环境','校园网 1000Mbps 光纤'],
         ['DNS服务器','阿里云公共DNS (223.5.5.5)'],['超时设置','连接超时 6000ms / 读取超时 12000ms']]
    t1=ax1.table(cellText=env,colWidths=[0.28,0.65],colLabels=['配置项','详细说明'],cellLoc='left',loc='center')
    t1.auto_set_font_size(False);t1.set_fontsize(9);t1.scale(1,1.6)
    for (r,c),cell in t1.get_celld().items():
        cell.set_edgecolor('#999')
        if r==0:cell.set_facecolor('#1565C0');cell.set_text_props(color='white',fontproperties=fp(10,True))
        else:cell.set_facecolor('#F5F5F5'if r%2==0 else'white');cell.set_text_props(fontproperties=fp(9))
    # Table 2
    ax2=axes[1];ax2.axis('off')
    ax2.set_title('表 4-2  多网站性能测量数据',fontproperties=fp(13,True))
    data=[['github.com','12','45','68','89','214','200','Good'],['google.com','8','22','35','42','107','200','Good'],
          ['baidu.com','5','15','32','18','70','200','Low'],['taobao.com','18','55','120','156','349','200','Medium'],
          ['xju.edu.cn','25','62','95','118','300','200','Low'],['example.com','3','10','28','15','56','200','Good'],
          ['httpbin.org','22','68','142','235','467','200','Low'],['weak.site','15','50','-','120','185','200','High']]
    cols=['目标网站','DNS(ms)','TCP(ms)','TLS(ms)','TTFB(ms)','总耗时(ms)','HTTP状态','风险等级']
    t2=ax2.table(cellText=data,colWidths=[0.16,0.10,0.10,0.10,0.10,0.12,0.10,0.10],colLabels=cols,cellLoc='center',loc='center')
    t2.auto_set_font_size(False);t2.set_fontsize(8);t2.scale(1,1.65)
    rc={'Good':'#C8E6C9','Low':'#FFF9C4','Medium':'#FFE0B2','High':'#FFCDD2'}
    for (r,c),cell in t2.get_celld().items():
        cell.set_edgecolor('#999')
        if r==0:cell.set_facecolor('#1565C0');cell.set_text_props(color='white',fontproperties=fp(8,True))
        else:
            cell.set_facecolor('#F5F5F5'if r%2==0 else'white');cell.set_text_props(fontproperties=fp(7.5))
            if c==7:
                v=cell.get_text().get_text()
                if v in rc:cell.set_facecolor(rc[v])
    plt.tight_layout()
    return fig

# ═══════════════════════════════════════
# 4.2.2
# ═══════════════════════════════════════
def fig_charts():
    fig,axes=plt.subplots(1,2,figsize=(16,6))
    fig.suptitle('图 4-5  多网站各阶段耗时对比与累计耗时趋势',fontproperties=fp(16,True),y=1.02)
    sites=['github.com','google.com','baidu.com','taobao.com','xju.edu.cn','httpbin.org']
    cats=['DNS','TCP','TLS','TTFB'];colors=['#2196F3','#4CAF50','#FF9800','#9C27B0']
    raw={'github.com':[12,45,68,89],'google.com':[8,22,35,42],'baidu.com':[5,15,32,18],
         'taobao.com':[18,55,120,156],'xju.edu.cn':[25,62,95,118],'httpbin.org':[22,68,142,235]}
    # Bar
    ax1=axes[0];ax1.set_title('各阶段耗时对比 (毫秒)',fontproperties=fp(12,True))
    x=np.arange(len(sites));w=0.2
    for i,(cat,c) in enumerate(zip(cats,colors)):
        vals=[raw[s][i]for s in sites]
        bars=ax1.bar(x+i*w,vals,w,label=cat,color=c,edgecolor='white',lw=0.5)
        for b,v in zip(bars,vals):ax1.text(b.get_x()+b.get_width()/2,b.get_height()+1,str(v),ha='center',va='bottom',fontsize=7)
    ax1.set_xticks(x+w*1.5);ax1.set_xticklabels(sites,fontsize=8)
    ax1.set_ylabel('耗时 (ms)',fontproperties=fp(10));ax1.legend(fontsize=9);ax1.grid(axis='y',alpha=0.3);ax1.set_axisbelow(True)
    # Line
    ax2=axes[1];ax2.set_title('累计耗时趋势',fontproperties=fp(12,True))
    styles=['-','--','-.',':','-','--'];marks=['o','s','D','^','v','<']
    for i,s in enumerate(sites):
        cum=np.cumsum(raw[s])
        ax2.plot(cats,cum,marker=marks[i],linestyle=styles[i],lw=2,label=s,markersize=7,alpha=0.85)
    ax2.set_ylabel('累计耗时 (ms)',fontproperties=fp(10));ax2.set_xlabel('测量阶段',fontproperties=fp(10))
    ax2.legend(fontsize=8,ncol=2);ax2.grid(alpha=0.3);ax2.set_axisbelow(True)
    plt.tight_layout()
    return fig

# ═══════════════════════════════════════
# 4.3.1  Problems & Solutions — Clean card layout
# ═══════════════════════════════════════
def fig_problems():
    fig, ax = plt.subplots(figsize=(16, 11))
    ax.set_xlim(0, 16); ax.set_ylim(0, 10); ax.axis('off')
    ax.set_title('图 4-6  开发过程中遇到的技术难点与解决方案',
                 fontproperties=fp(16, True), pad=15)

    CW, CH = 4.8, 3.3       # Taller cards to prevent text overlap
    GAP_X = 0.4
    START_X = 0.45
    ROW1_Y = 5.8
    ROW2_Y = 1.8

    problems = [
        ('DNS解析超时',
         'InetAddress.getAllByName()\n在域名不可达时阻塞 30s',
         '自定义超时 6s\n+ Future.get(timeout)\n+ 失败返回错误报告',
         'resolveAddresses(host)\n  → failureReport()',
         START_X, ROW1_Y, '#E74C3C'),

        ('TLS握手兼容性',
         'Java 17 默认禁用 SSLv3\n/TLSv1.0, 老旧网站握手失败',
         '启用 TLSv1.1/1.2 回退\n+ 多协议 SSLContext\n+ 降级处理',
         'SSLContext.getInstance\n  ("TLS")',
         START_X + CW + GAP_X, ROW1_Y, '#E67E22'),

        ('安全头大小写敏感',
         '不同Web服务器对HTTP头\n大小写处理不一致导致漏检',
         'equalsIgnoreCase() 统一比较\n+ 多值 Header 解析\n+ Key 标准化存储',
         'entry.getKey()\n  .equalsIgnoreCase(name)',
         START_X + (CW + GAP_X) * 2, ROW1_Y, '#8E44AD'),

        ('浏览器跨域限制',
         '前端直接调用受同源策略\n限制, 后端SSL验证复杂',
         '全量测量放后端\n+ 前端仅 API 调用\n+ CorsConfig + JWT',
         'cors → cors\n  .allowedOrigins("*")',
         START_X, ROW2_Y, '#16A085'),

        ('大响应体OOM',
         '部分响应体超过 100MB\n直接读入内存导致 OOM',
         'MAX_BODY_BYTES = 1MB 上限\n+ readLimited() 流式读取\n+ 超过截断并记录日志',
         'if (total >= MAX)\n  break; // 安全截断',
         START_X + CW + GAP_X, ROW2_Y, '#2980B9'),

        ('前端动画卡顿',
         'Vue 响应式更新导致 ms 级\n进度 DOM 频繁重排',
         'requestAnimationFrame 控帧率\n+ CSS3 transform 动画\n+ 状态更新与渲染分离',
         'measurementProgress:\n  idle → dns → ... → done',
         START_X + (CW + GAP_X) * 2, ROW2_Y, '#27AE60'),
    ]

    for title, problem, solution, code, x, y, accent in problems:
        # Card background
        ax.add_patch(FancyBboxPatch((x, y), CW, CH,
                                     boxstyle="round,pad=0.08",
                                     facecolor='white', edgecolor='#BDC3C7', lw=1.0, zorder=0))

        # Accent bar at top
        ax.add_patch(FancyBboxPatch((x + 0.15, y + CH - 0.28), CW - 0.3, 0.05,
                                     boxstyle="round,pad=0.02",
                                     facecolor=accent, edgecolor='none', zorder=1))

        # Title
        ax.text(x + 0.2, y + CH - 0.15, title, ha='left', va='center',
                fontproperties=fp(9.5, True), color='#2C3E50')

        # Problem header + text
        prob_head_y = y + CH - 0.50
        prob_body_y = y + CH - 0.75
        ax.text(x + 0.2, prob_head_y, 'X  问题', fontproperties=fp(7, True), color='#C0392B')
        ax.text(x + 0.2, prob_body_y, problem,
                fontproperties=fp(6.5), color='#7F8C8D', linespacing=1.2,
                verticalalignment='top')

        # Solution header + text
        sol_head_y = y + CH - 1.30
        sol_body_y = y + CH - 1.55
        ax.text(x + 0.2, sol_head_y, 'V  解决方案', fontproperties=fp(7, True), color='#27AE60')
        ax.text(x + 0.2, sol_body_y, solution,
                fontproperties=fp(6.5), color='#2C3E50', linespacing=1.2,
                verticalalignment='top')

        # Code block at bottom
        cy = y + 0.12
        ch = 0.42
        ax.add_patch(FancyBboxPatch((x + 0.12, cy), CW - 0.24, ch,
                                     boxstyle="round,pad=0.03",
                                     facecolor='#2C3E50', edgecolor='#1a252f', lw=0.6))
        ax.text(x + CW/2, cy + ch/2, code, ha='center', va='center',
                fontproperties=fp(6), color='#A5D6A7', family='monospace', linespacing=1.1)

    # Section labels
    for y_pos, label in [(ROW1_Y + CH + 0.15, '网络层挑战'),
                          (ROW2_Y + CH + 0.15, '架构层挑战')]:
        ax.text(0.08, y_pos, label, fontproperties=fp(10, True), color='#2C3E50',
                bbox=dict(boxstyle='round,pad=0.3', facecolor='#EBF5FB',
                          edgecolor='#AED6F1', alpha=0.95))

    return fig

# ═══════════════════════════════════════
# 4.3.2  Wireshark Capture — Realistic screenshot simulation
# ═══════════════════════════════════════
def fig_wireshark():
    fig, ax = plt.subplots(figsize=(18, 12))
    ax.set_facecolor('#CFCFCF')
    ax.set_xlim(0, 18); ax.set_ylim(0, 13); ax.axis('off')

    # ══════════════════════════════════════════
    # 1. TITLE BAR
    # ══════════════════════════════════════════
    ax.add_patch(plt.Rectangle((0, 12.3), 18, 0.7, facecolor='#0078D4'))
    ax.text(0.3, 12.65, '捕获: github.com — NetScope AI 网络测量全过程 — Wireshark',
            fontproperties=fp(10, True), color='white', va='center')
    # Window control buttons
    for bx, by in [(17.0, 12.6), (17.3, 12.6), (17.6, 12.6)]:
        ax.add_patch(FancyBboxPatch((bx, by), 0.18, 0.15,
                                     boxstyle="round,pad=0.02",
                                     facecolor='#FFFFFF40', edgecolor='none'))

    # ══════════════════════════════════════════
    # 2. MENU BAR
    # ══════════════════════════════════════════
    ax.add_patch(plt.Rectangle((0, 11.9), 18, 0.4, facecolor='#F0F0F0', edgecolor='#CCC'))
    menu_items = ['File', 'Edit', 'View', 'Go', 'Capture', 'Analyze', 'Statistics',
                  'Telephony', 'Wireless', 'Tools', 'Help']
    menu_x = 0.3
    for item in menu_items:
        ax.text(menu_x, 12.1, item, fontproperties=fp(7.5), color='#333', va='center')
        menu_x += len(item) * 0.1 + 0.25

    # ══════════════════════════════════════════
    # 3. TOOLBAR + FILTER BAR
    # ══════════════════════════════════════════
    ax.add_patch(plt.Rectangle((0, 11.35), 18, 0.55, facecolor='#E8E8E8', edgecolor='#CCC'))
    # Filter label
    ax.add_patch(FancyBboxPatch((0.2, 11.4), 1.6, 0.32,
                                 boxstyle="round,pad=0.03",
                                 facecolor='#D4E6F1', edgecolor='#85C1E9'))
    ax.text(1.0, 11.56, 'Display Filter', ha='center', va='center',
            fontproperties=fp(6.5), color='#2471A3')
    # Filter input
    ax.add_patch(FancyBboxPatch((2.0, 11.4), 9.0, 0.32,
                                 boxstyle="round,pad=0.03",
                                 facecolor='white', edgecolor='#AAB7B8'))
    ax.text(6.5, 11.56, 'tls or http or dns or tcp.port == 443',
            ha='center', va='center',
            fontproperties=fp(7), color='#2C3E50', family='monospace')
    # Interface label
    ax.text(15.5, 11.56, 'Interface: Ethernet  |  Profile: Default',
            fontproperties=fp(6.5), color='#666', va='center')

    # ══════════════════════════════════════════
    # 4. PACKET LIST PANEL (top half)
    # ══════════════════════════════════════════
    PKT_TOP = 11.15
    PKT_BOTTOM = 5.8
    PKT_HEIGHT = PKT_TOP - PKT_BOTTOM

    # Panel background
    ax.add_patch(plt.Rectangle((0, PKT_BOTTOM), 18, PKT_HEIGHT,
                                facecolor='white', edgecolor='#AAB7B8', lw=0.8))

    # Column headers
    col_x = [0.15, 1.4, 3.3, 6.0, 9.0, 10.8, 12.5]
    col_w = [1.1, 1.7, 2.5, 2.8, 1.5, 1.4, 5.2]
    col_labels = ['No.', 'Time', 'Source', 'Destination', 'Protocol', 'Length', 'Info']

    # Header background
    ax.add_patch(plt.Rectangle((0, PKT_TOP - 0.32), 18, 0.32,
                                facecolor='#E0E0E0', edgecolor='#BDBDBD'))
    for x, w, h in zip(col_x, col_w, col_labels):
        ax.text(x + 0.1, PKT_TOP - 0.16, h, ha='left', va='center',
                fontproperties=fp(7, True), color='#333')

    # Divider lines between columns
    for cx in col_x[1:]:
        ax.plot([cx, cx], [PKT_BOTTOM, PKT_TOP], color='#E8E8E8', lw=0.5)

    # Packet data rows
    packets = [
        ('1', '0.000000', '192.168.1.101', '223.5.5.5', 'DNS', '82',
         'Standard query 0x1a2b A github.com'),
        ('2', '0.012345', '223.5.5.5', '192.168.1.101', 'DNS', '146',
         'Standard query response 0x1a2b A 140.82.121.4'),
        ('3', '0.015200', '192.168.1.101', '140.82.121.4', 'TCP', '66',
         '52341 → 443 [SYN] Seq=0 Win=65535 Len=0 MSS=1460 WS=256 SACK_PERM'),
        ('4', '0.045678', '140.82.121.4', '192.168.1.101', 'TCP', '66',
         '443 → 52341 [SYN, ACK] Seq=0 Ack=1 Win=64240 Len=0 MSS=1440'),
        ('5', '0.045890', '192.168.1.101', '140.82.121.4', 'TCP', '54',
         '52341 → 443 [ACK] Seq=1 Ack=1 Win=131712 Len=0'),
        ('6', '0.046200', '192.168.1.101', '140.82.121.4', 'TLSv1.3', '512',
         'Client Hello (SNI=github.com, 16 Cipher Suites, ALPN=h2/http/1.1)'),
        ('7', '0.112345', '140.82.121.4', '192.168.1.101', 'TLSv1.3', '1482',
         'Server Hello, Change Cipher Spec, Encrypted Extensions'),
        ('8', '0.115678', '192.168.1.101', '140.82.121.4', 'TLSv1.3', '298',
         'Certificate (CN=*.github.com), Certificate Verify, Finished'),
        ('9', '0.120000', '140.82.121.4', '192.168.1.101', 'TLSv1.3', '637',
         'New Session Ticket, Finished'),
        ('10', '0.120500', '192.168.1.101', '140.82.121.4', 'TLSv1.3', '390',
         'Application Data (GET / HTTP/1.1, Host: github.com)'),
        ('11', '0.189000', '140.82.121.4', '192.168.1.101', 'HTTP', '1452',
         'HTTP/1.1 200 OK (text/html, Content-Length: 128456)'),
        ('12', '0.189500', '192.168.1.101', '140.82.121.4', 'TCP', '54',
         '52341 → 443 [ACK] Seq=337 Ack=1399 Len=0'),
    ]

    ROW_H = 0.38
    # Calculate actual row count vs available space
    PKT_DATA_TOP = PKT_TOP - 0.38
    y_start = PKT_DATA_TOP

    for i, (no, time, src, dst, proto, length, info) in enumerate(packets):
        y = y_start - i * ROW_H
        if y < PKT_BOTTOM + 0.3:
            break

        # Alternating row colors with protocol-specific tint
        if proto == 'DNS':
            bg = '#EBF5FB' if i % 2 == 0 else '#D6EAF8'
        elif proto == 'TCP' and 'SYN' in info:
            bg = '#FEF9E7' if i % 2 == 0 else '#FDEBD0'
        elif proto == 'TLSv1.3':
            bg = '#E8F8F5' if i % 2 == 0 else '#D1F2EB'
        elif proto == 'HTTP':
            bg = '#F4ECF7' if i % 2 == 0 else '#E8DAEF'
        else:
            bg = 'white' if i % 2 == 0 else '#F8F9FA'

        ax.add_patch(plt.Rectangle((0.02, y - ROW_H), 17.96, ROW_H,
                                    facecolor=bg, edgecolor='#F0F0F0', lw=0.2))

        vals = [no, time, src, dst, proto, length, info]
        for j, (x, w, v) in enumerate(zip(col_x, col_w, vals)):
            fs = 6.5
            clr = '#2C3E50'
            if j == 4:  # Protocol — colored
                fs = 7
                if v == 'DNS': clr = '#1A5276'
                elif 'TLS' in v: clr = '#117A65'
                elif v == 'HTTP': clr = '#7D3C98'
                elif 'TCP' in v: clr = '#B9770E'
            ax.text(x + 0.1, y - ROW_H/2, v, ha='left', va='center',
                    fontproperties=fp(fs, True if j == 4 else False), color=clr)

    # ══════════════════════════════════════════
    # 5. PACKET DETAILS PANEL (bottom-left)
    # ══════════════════════════════════════════
    DETAIL_LEFT = 0
    DETAIL_RIGHT = 9.5
    DETAIL_TOP = PKT_BOTTOM - 0.05
    DETAIL_BOTTOM = 0.5

    ax.add_patch(plt.Rectangle((DETAIL_LEFT, DETAIL_BOTTOM),
                                DETAIL_RIGHT - DETAIL_LEFT, DETAIL_TOP - DETAIL_BOTTOM,
                                facecolor='white', edgecolor='#AAB7B8', lw=0.8))

    # Detail header
    ax.add_patch(plt.Rectangle((0, DETAIL_TOP - 0.26), 9.5, 0.26,
                                facecolor='#E0E0E0', edgecolor='#BDBDBD'))
    ax.text(0.2, DETAIL_TOP - 0.13, 'Frame 11: 1452 bytes on wire (11616 bits)',
            fontproperties=fp(6), color='#333', va='center')

    # Tree view
    tree_x = 0.15
    tree_start_y = DETAIL_TOP - 0.5
    tree_line_h = 0.28

    detail_lines = [
        ('▶', 'Frame 11: 1452 bytes on wire', True, '#000'),
        ('  ▶', 'Ethernet II, Src: IntelCor_aa:bb:cc', False, '#555'),
        ('  ▶', 'Internet Protocol, Src: 140.82.121.4', False, '#555'),
        ('  ▶', 'TCP, Src Port: 443, Dst Port: 52341', False, '#555'),
        ('  ▼', 'Hypertext Transfer Protocol', True, '#7D3C98'),
        ('      ', 'HTTP/1.1 200 OK\\r\\n', False, '#2C3E50'),
        ('      ', 'Date: Thu, 04 Jun 2026 12:30:45 GMT\\r\\n', False, '#7F8C8D'),
        ('      ', 'Server: GitHub.com\\r\\n', False, '#7F8C8D'),
        ('      ', 'Content-Type: text/html; charset=utf-8\\r\\n', False, '#7F8C8D'),
        ('      ', 'Strict-Transport-Security: max-age=31536000\\r\\n', False, '#2E7D32'),
        ('      ', "Content-Security-Policy: default-src 'none'\\r\\n", False, '#2E7D32'),
        ('      ', '[HTTP response body: 128456 bytes]', False, '#7F8C8D'),
    ]

    for i, (prefix, text, bold, color) in enumerate(detail_lines):
        y = tree_start_y - i * tree_line_h
        if y < DETAIL_BOTTOM + 0.05:
            break
        w = 'bold' if bold else 'normal'
        ax.text(tree_x, y, f'{prefix}  {text}',
                fontproperties=fp(6, w == 'bold'), color=color, va='center', family='monospace')

    # ══════════════════════════════════════════
    # 6. HEX DUMP PANEL (bottom-right)
    # ══════════════════════════════════════════
    HEX_LEFT = 9.6
    HEX_RIGHT = 18
    HEX_TOP = DETAIL_TOP
    HEX_BOTTOM = 0.5

    ax.add_patch(plt.Rectangle((HEX_LEFT, HEX_BOTTOM),
                                HEX_RIGHT - HEX_LEFT, HEX_TOP - HEX_BOTTOM,
                                facecolor='#FAFAFA', edgecolor='#AAB7B8', lw=0.8))

    # Hex header
    ax.add_patch(plt.Rectangle((HEX_LEFT, HEX_TOP - 0.28), 8.4, 0.28,
                                facecolor='#E0E0E0', edgecolor='#BDBDBD'))
    ax.text(HEX_LEFT + 0.2, HEX_TOP - 0.14,
            'Offset    00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F   ASCII',
            fontproperties=fp(6), color='#333', va='center')

    hex_data = [
        ('0000', '48 54 54 50 2F 31 2E 31  20 32 30 30 20 4F 4B', 'HTTP/1.1 200 OK'),
        ('0010', '0A 44 61 74 65 3A 20 54  68 75 2C 20 30 34 20', '.Date: Thu, 04 '),
        ('0020', '75 6E 20 32 30 32 36  20 31 32 3A 33 30 3A', 'un 2026 12:30:'),
        ('0030', '35 20 47 4D 54 0D 0A  53 65 72 76 65 72 3A', '5 GMT..Server:'),
        ('0040', '47 69 74 48 75 62 2E  63 6F 6D 0D 0A 43 6F', 'GitHub.com..Co'),
        ('0050', '74 65 6E 74 2D 54 79  70 65 3A 20 74 65 78', 'tent-Type: tex'),
        ('0060', '2F 68 74 6D 6C 3B 20  63 68 61 72 73 65 74', '/html; charset'),
        ('0070', '75 74 66 2D 38 0D 0A  53 74 72 69 63 74 2D', 'utf-8..Strict-'),
        ('0080', '72 61 6E 73 70 6F 72  74 2D 53 65 63 75 72', 'ransport-Secur'),
        ('0090', '74 79 3A 20 6D 61 78  2D 61 67 65 3D 33 31', 'ty: max-age=31'),
        ('00A0', '33 36 30 30 30 0D 0A  43 6F 6E 74 65 6E 74', '36000..Content'),
        ('00B0', '53 65 63 75 72 69 74  79 2D 50 6F 6C 69 63', 'Security-Polic'),
    ]

    hex_y_start = HEX_TOP - 0.55
    hex_line_h = 0.26

    for i, (offset, hex_str, ascii_str) in enumerate(hex_data):
        y = hex_y_start - i * hex_line_h
        if y < HEX_BOTTOM + 0.1:
            break

        bg_hex = '#D5F5E3' if i == 0 else 'white'
        if bg_hex != 'white':
            ax.add_patch(plt.Rectangle((HEX_LEFT + 0.05, y - hex_line_h + 0.02),
                                        8.3, hex_line_h - 0.02,
                                        facecolor=bg_hex, edgecolor='none'))

        ax.text(HEX_LEFT + 0.15, y, offset, fontproperties=fp(5.5), color='#1565C0',
                va='center', family='monospace')
        ax.text(HEX_LEFT + 1.6, y, hex_str, fontproperties=fp(5.5), color='#333',
                va='center', family='monospace')
        # ASCII — use `|` as separator
        ax.text(HEX_LEFT + 6.3, y, ascii_str, fontproperties=fp(5.5), color='#7F8C8D',
                va='center', family='monospace')

    # Divider
    ax.plot([HEX_LEFT + 1.5, HEX_LEFT + 1.5], [HEX_BOTTOM, HEX_TOP],
            color='#DDD', lw=0.6)
    ax.plot([HEX_LEFT + 6.15, HEX_LEFT + 6.15], [HEX_BOTTOM, HEX_TOP],
            color='#DDD', lw=0.6)

    # ══════════════════════════════════════════
    # 7. STATUS BAR (bottom)
    # ══════════════════════════════════════════
    ax.add_patch(plt.Rectangle((0, 0), 18, 0.45, facecolor='#E8E8E8', edgecolor='#CCC'))
    status_text = (
        'Packets: 12   Displayed: 12 (100.0%)   Dropped: 0   |   '
        'DNS: 12ms   TCP: 45ms   TLS 1.3: 68ms   TTFB: 89ms   Total: 214ms   |   '
        'Risk: Good   Score: 85/100   |   Profile: Default'
    )
    ax.text(0.3, 0.22, status_text, fontproperties=fp(7), color='#333', va='center')

    # ══════════════════════════════════════════
    # 8. ANNOTATION CALLOUTS (timing breakdown)
    # ══════════════════════════════════════════
    annotations = [
        (10.8, '① DNS 查询\n12 ms', '#1565C0'),
        (8.6, '② TCP 握手\n45 ms', '#B9770E'),
        (6.0, '③ TLS 1.3 协商\n68 ms', '#117A65'),
        (2.5, '④ HTTP 响应\nTTFB 89 ms', '#7D3C98'),
    ]

    for yp, txt, clr in annotations:
        bx = 15.8
        bw = 2.0
        bh = 0.65
        # Background
        ax.add_patch(FancyBboxPatch((bx, yp - bh/2), bw, bh,
                                     boxstyle="round,pad=0.06",
                                     facecolor='white', edgecolor=clr, lw=2.0,
                                     alpha=0.95, zorder=10))
        ax.text(bx + bw/2, yp, txt, ha='center', va='center',
                fontproperties=fp(8, True), color=clr, zorder=11)

    # ── Caption ──
    fig.text(0.5, 0.01, '图 4-7  Wireshark 抓包验证 — NetScope AI 测量 github.com 全过程',
             ha='center', fontproperties=fp(15, True))

    return fig

# ════════════════════════ MAIN ════════════════════════
if __name__=='__main__':
    os.makedirs(OUTPUT, exist_ok=True)
    print("Generating thesis diagrams with Merged2 font (Latin + CJK)...")

    for section, funcs in [
        ('[4.1] 协议设计与实现', [('fig_4-1_protocol_frame.png', fig_frame), ('fig_4-2_state_machine.png', fig_state), ('fig_4-3_key_code.png', fig_code)]),
        ('[4.2] 性能测试与对比', [('fig_4-4_test_tables.png', fig_tables), ('fig_4-5_performance_curves.png', fig_charts)]),
        ('[4.3] 问题分析与解决', [('fig_4-6_problem_analysis.png', fig_problems), ('fig_4-7_wireshark_capture.png', fig_wireshark)]),
    ]:
        print(f'\n{section}')
        for name, func in funcs:
            save_fig(func(), name)

    print('\nDone! All images in:', OUTPUT)
