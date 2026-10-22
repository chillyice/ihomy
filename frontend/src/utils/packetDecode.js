// 网络报文自动解码：识别以太网 / IPv4 / IPv6 / ARP / TCP / UDP / ICMP / DNS / HTTP / TLS，
// 逐字段给出偏移、长度、值与含义。纯函数，不碰 DOM / 网络 / 存储。
export const PACKET_MAX_BYTES = 65536;
export class PacketError extends Error {
    constructor(code, extra = {}) {
        super(code);
        this.name = 'PacketError';
        this.code = code;
        this.limit = extra.limit;
    }
}
function pushHexToken(out, token) {
    const clean = token.replace(/0x/gi, '').replace(/\\x/gi, '');
    if (!/^[0-9a-fA-F]+$/.test(clean) || clean.length % 2)
        return false;
    for (let i = 0; i < clean.length; i += 2)
        out.push(clean.slice(i, i + 2).toUpperCase());
    return true;
}
function scanHexLine(line) {
    let source = line.trim();
    if (!source)
        return { tokens: [], hexdump: false };
    const hexdump = /^(?:0x)?[0-9a-fA-F]{4,16}\s*[:\s]\s{2,}|^\s*(?:0x)?[0-9a-fA-F]{4,16}:/.test(line) || /\|.*\|/.test(source);
    source = source.replace(/\|.*$/, '').trim();
    source = source.replace(/^(?:0x)?[0-9a-fA-F]{4,16}\s*:[ \t]*/, '');
    source = source.replace(/^(?:0x)?[0-9a-fA-F]{4,16}[ \t]{2,}/, '');
    const segment = hexdump ? source.split(/[ \t]{2,}/)[0] : source;
    const parts = segment.split(/[\s,;:，、]+/).filter(Boolean);
    const tokens = [];
    for (const part of parts)
        if (!pushHexToken(tokens, part))
            return null;
    return { tokens, hexdump };
}
/** 十六进制报文 → 字节：接受空格/逗号/冒号/换行分隔，也接受连续写法与 0xAA、\xAA 前缀 */
export function parseHexBytes(text, maxBytes = PACKET_MAX_BYTES) {
    const cleaned = String(text)
        .replace(/0x/gi, '')
        .replace(/\\x/gi, '')
        .replace(/[\s,;:，、]+/g, '');
    if (!cleaned)
        return new Uint8Array(0);
    if (!/^[0-9a-fA-F]+$/.test(cleaned))
        throw new PacketError('invalidHex');
    if (cleaned.length % 2)
        throw new PacketError('oddHex');
    if (cleaned.length / 2 > maxBytes)
        throw new PacketError('tooLong', { limit: maxBytes });
    const bytes = new Uint8Array(cleaned.length / 2);
    for (let i = 0; i < bytes.length; i++)
        bytes[i] = parseInt(cleaned.slice(i * 2, i * 2 + 2), 16);
    return bytes;
}
const asciiChar = (byte) => (byte === 0x20 ? '␠' : byte > 0x20 && byte < 0x7f ? String.fromCharCode(byte) : '·');
const asciiOfBytes = (bytes) => Array.from(bytes, asciiChar).join('');
/**
 * 自动识别输入形态并保留行结构：纯 HEX、带偏移/ASCII 列的十六进制转储、普通文本。
 * 左侧原文与 ASCII 按输入行一一对应，方便逐行对照。
 */
export function parsePacketInput(text, mode = 'auto', maxBytes = PACKET_MAX_BYTES) {
    const source = String(text).replace(/\r\n/g, '\n');
    if (!source.trim())
        return { mode: 'hex', kind: 'hex', bytes: new Uint8Array(0), lines: [] };
    if (mode !== 'text') {
        const rawLines = source.split('\n');
        while (rawLines.length && !rawLines[rawLines.length - 1].trim())
            rawLines.pop();
        const scans = rawLines.map(scanHexLine);
        const allHex = scans.length > 0 && scans.every((scan) => scan !== null);
        if (mode === 'hex' || allHex) {
            if (!allHex) {
                const bytes = parseHexBytes(source, maxBytes);
                const ascii = asciiOfBytes(bytes);
                return {
                    mode: 'hex',
                    kind: 'hex',
                    bytes,
                    lines: [{ offset: 0, text: source.trim(), hex: hexOfBytes(bytes), ascii, bytes: Array.from(bytes) }],
                };
            }
            const lines = [];
            let offset = 0;
            let kind = 'hex';
            for (let i = 0; i < rawLines.length; i++) {
                const scan = scans[i];
                if (scan.hexdump)
                    kind = 'hexdump';
                const bytes = Uint8Array.from(scan.tokens.map((token) => parseInt(token, 16)));
                if (offset + bytes.length > maxBytes)
                    throw new PacketError('tooLong', { limit: maxBytes });
                lines.push({
                    offset,
                    text: rawLines[i].trim(),
                    hex: hexOfBytes(bytes),
                    ascii: asciiOfBytes(bytes),
                    bytes: Array.from(bytes),
                });
                offset += bytes.length;
            }
            const bytes = new Uint8Array(offset);
            for (const line of lines)
                bytes.set(line.bytes, line.offset);
            return { mode: 'hex', kind, bytes, lines };
        }
    }
    const encoder = new TextEncoder();
    const lines = [];
    let offset = 0;
    for (const raw of source.split('\n')) {
        const encoded = encoder.encode(raw);
        lines.push({
            offset,
            text: raw,
            hex: hexOfBytes(encoded),
            ascii: asciiOfBytes(encoded),
            bytes: Array.from(encoded),
        });
        offset += encoded.length;
        if (offset > maxBytes)
            throw new PacketError('tooLong', { limit: maxBytes });
    }
    const bytes = new Uint8Array(offset);
    for (const line of lines)
        bytes.set(line.bytes, line.offset);
    return { mode: 'text', kind: 'text', bytes, lines };
}
const ETHERTYPES = {
    0x0800: 'IPv4',
    0x0806: 'ARP',
    0x8100: '802.1Q VLAN',
    0x86dd: 'IPv6',
    0x8847: 'MPLS',
    0x8863: 'PPPoE Discovery',
    0x8864: 'PPPoE Session',
    0x88a8: '802.1ad QinQ',
};
const IP_PROTOCOLS = {
    1: 'ICMP',
    2: 'IGMP',
    6: 'TCP',
    17: 'UDP',
    41: 'IPv6',
    47: 'GRE',
    50: 'ESP',
    51: 'AH',
    58: 'ICMPv6',
    89: 'OSPF',
    132: 'SCTP',
};
const SERVICES = {
    20: 'FTP-DATA',
    21: 'FTP',
    22: 'SSH',
    23: 'TELNET',
    25: 'SMTP',
    53: 'DNS',
    67: 'DHCP',
    68: 'DHCP',
    80: 'HTTP',
    110: 'POP3',
    123: 'NTP',
    143: 'IMAP',
    161: 'SNMP',
    162: 'SNMP Trap',
    443: 'HTTPS',
    465: 'SMTPS',
    587: 'SMTP',
    993: 'IMAPS',
    995: 'POP3S',
    1883: 'MQTT',
    3306: 'MySQL',
    3389: 'RDP',
    5432: 'PostgreSQL',
    6379: 'Redis',
    8080: 'HTTP Alt',
    8443: 'HTTPS Alt',
};
const DNS_TYPES = {
    1: 'A',
    2: 'NS',
    5: 'CNAME',
    6: 'SOA',
    12: 'PTR',
    15: 'MX',
    16: 'TXT',
    28: 'AAAA',
    33: 'SRV',
    41: 'OPT',
};
const ICMP_TYPES = {
    0: { zh: '回显应答（ping 回复）', en: 'Echo reply (ping response)' },
    3: { zh: '目标不可达', en: 'Destination unreachable' },
    5: { zh: '重定向', en: 'Redirect' },
    8: { zh: '回显请求（ping）', en: 'Echo request (ping)' },
    11: { zh: '超时', en: 'Time exceeded' },
    12: { zh: '参数问题', en: 'Parameter problem' },
};
const ICMPV6_TYPES = {
    1: { zh: '目标不可达', en: 'Destination unreachable' },
    2: { zh: '数据包过大', en: 'Packet too big' },
    3: { zh: '超时', en: 'Time exceeded' },
    4: { zh: '参数问题', en: 'Parameter problem' },
    128: { zh: '回显请求（ping）', en: 'Echo request (ping)' },
    129: { zh: '回显应答', en: 'Echo reply' },
    133: { zh: '路由器请求', en: 'Router solicitation' },
    134: { zh: '路由器通告', en: 'Router advertisement' },
    135: { zh: '邻居请求', en: 'Neighbor solicitation' },
    136: { zh: '邻居通告', en: 'Neighbor advertisement' },
};
const EXT_HEADERS = {
    0: 'Hop-by-Hop Options',
    43: 'Routing',
    44: 'Fragment',
    60: 'Destination Options',
    51: 'Authentication',
};
const u16 = (b, i) => ((b[i] ?? 0) << 8) | (b[i + 1] ?? 0);
const u32 = (b, i) => (((b[i] ?? 0) << 24) | ((b[i + 1] ?? 0) << 16) | ((b[i + 2] ?? 0) << 8) | (b[i + 3] ?? 0)) >>> 0;
const hex2 = (n) => n.toString(16).toUpperCase().padStart(2, '0');
const hex4 = (n) => n.toString(16).toUpperCase().padStart(4, '0');
export function hexOfBytes(b, offset = 0, length = b.length) {
    const end = Math.min(b.length, offset + Math.max(0, length));
    const out = [];
    for (let i = Math.max(0, offset); i < end; i++)
        out.push(hex2(b[i]));
    return out.join(' ');
}
const macOf = (b, i) => [0, 1, 2, 3, 4, 5].map((n) => hex2(b[i + n] ?? 0)).join(':');
const ipv4Of = (b, i) => `${b[i] ?? 0}.${b[i + 1] ?? 0}.${b[i + 2] ?? 0}.${b[i + 3] ?? 0}`;
function ipv6Of(b, i) {
    const words = [];
    for (let n = 0; n < 8; n++)
        words.push(((b[i + n * 2] ?? 0) << 8 | (b[i + n * 2 + 1] ?? 0)).toString(16));
    let bestStart = -1;
    let bestLen = 0;
    for (let n = 0; n < 8; n++) {
        if (words[n] !== '0')
            continue;
        let end = n;
        while (end < 8 && words[end] === '0')
            end++;
        if (end - n > bestLen) {
            bestStart = n;
            bestLen = end - n;
        }
        n = end;
    }
    if (bestLen < 2)
        return words.join(':');
    return `${words.slice(0, bestStart).join(':')}::${words.slice(bestStart + bestLen).join(':')}`;
}
function printable(b, offset, length, max = 160) {
    const end = Math.min(b.length, offset + Math.min(length, max));
    let out = '';
    for (let i = offset; i < end; i++) {
        const c = b[i];
        out += c >= 0x20 && c < 0x7f ? String.fromCharCode(c) : c === 0x0a ? '\\n' : c === 0x0d ? '\\r' : c === 0x09 ? '\\t' : '·';
    }
    return out + (length > max ? '…' : '');
}
function inetChecksum(b, start, end) {
    let sum = 0;
    const stop = Math.min(end, b.length);
    for (let i = start; i < stop; i += 2)
        sum += (b[i] << 8) | (i + 1 < stop ? b[i + 1] : 0);
    while (sum >> 16)
        sum = (sum & 0xffff) + (sum >> 16);
    return ~sum & 0xffff;
}
const serviceLabel = (port) => (SERVICES[port] ? `${port} · ${SERVICES[port]}` : String(port));
const protocolLabel = (proto) => (IP_PROTOCOLS[proto] ? `${proto} · ${IP_PROTOCOLS[proto]}` : String(proto));
function isEthernet(b) {
    if (b.length < 14)
        return false;
    const type = u16(b, 12);
    if (!(type in ETHERTYPES))
        return false;
    if (type === 0x0800)
        return b.length >= 34 && (b[14] >> 4) === 4;
    if (type === 0x86dd)
        return b.length >= 54 && (b[14] >> 4) === 6;
    if (type === 0x0806)
        return b.length >= 42;
    return true;
}
function isIPv4(b) {
    if (b.length < 20 || (b[0] >> 4) !== 4)
        return false;
    const ihl = (b[0] & 0x0f) * 4;
    const total = u16(b, 2);
    return ihl >= 20 && ihl <= b.length && (total === 0 || total >= ihl);
}
function isIPv6(b) {
    if (b.length < 40 || (b[0] >> 4) !== 6)
        return false;
    const payload = u16(b, 4);
    return payload === 0 || 40 + payload <= b.length;
}
function isArp(b) {
    return b.length >= 28 && u16(b, 0) === 1 && u16(b, 2) === 0x0800 && b[4] === 6 && b[5] === 4;
}
export function analyzePacket(b, locale) {
    const zh = locale === 'zh';
    const L = (a, c) => (zh ? a : c);
    const layers = [];
    const warnings = [];
    const stack = [];
    let kind = 'unknown';
    const addLayer = (name, fields, note) => {
        layers.push(note ? { name, note, fields } : { name, fields });
        stack.push(name);
    };
    const field = (offset, length, name, value, meaning) => ({
        offset,
        length,
        name,
        value,
        meaning,
        hex: hexOfBytes(b, offset, Math.min(length, 24)),
    });
    const payloadField = (offset, length, meaning) => field(offset, length, L('负载', 'Payload'), `${length} B · ${printable(b, offset, length, 80) || '—'}`, meaning);
    function decodeApp(offset, end, proto, srcPort, dstPort) {
        const length = end - offset;
        if (length <= 0)
            return;
        if (srcPort === 53 || dstPort === 53) {
            if (proto === 'tcp') {
                if (length >= 2)
                    decodeDns(offset + 2, Math.min(end, offset + 2 + u16(b, offset)), 'DNS (TCP)');
            }
            else {
                decodeDns(offset, end, 'DNS');
            }
            return;
        }
        const head = printable(b, offset, Math.min(length, 24), 24);
        if (/^(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH|TRACE|CONNECT) \S+ HTTP\/\d/i.test(head) || /^HTTP\/\d/i.test(head)) {
            decodeHttp(offset, end);
            return;
        }
        if (b[offset] === 0x16 && b[offset + 1] === 0x03) {
            decodeTls(offset, end);
            return;
        }
        addLayer(L('应用数据', 'Application data'), [payloadField(offset, length, L(`端口 ${srcPort} → ${dstPort}；未识别应用层协议，可按 ASCII 预览查看`, `Port ${srcPort} → ${dstPort}; no known application protocol, use the ASCII preview`))]);
    }
    function decodeTcp(offset, end) {
        if (offset + 20 > end) {
            warnings.push(L('TCP 首部不完整，无法继续解析。', 'TCP header is truncated.'));
            return;
        }
        const srcPort = u16(b, offset);
        const dstPort = u16(b, offset + 2);
        const seq = u32(b, offset + 4);
        const ack = u32(b, offset + 8);
        const dataOffset = (b[offset + 12] >> 4) * 4;
        const flags = [];
        const bits = [
            [offset + 12, 0x01, 'NS'],
            [offset + 13, 0x80, 'CWR'],
            [offset + 13, 0x40, 'ECE'],
            [offset + 13, 0x20, 'URG'],
            [offset + 13, 0x10, 'ACK'],
            [offset + 13, 0x08, 'PSH'],
            [offset + 13, 0x04, 'RST'],
            [offset + 13, 0x02, 'SYN'],
            [offset + 13, 0x01, 'FIN'],
        ];
        for (const [at, mask, name] of bits)
            if (b[at] & mask)
                flags.push(name);
        const fields = [
            field(offset, 2, L('源端口', 'Source port'), serviceLabel(srcPort), L('发送方 TCP 端口', 'Sender TCP port')),
            field(offset + 2, 2, L('目的端口', 'Destination port'), serviceLabel(dstPort), L('接收方 TCP 端口', 'Receiver TCP port')),
            field(offset + 4, 4, L('序号', 'Sequence'), String(seq), L('本段第一个字节的序号', 'Sequence number of the first data byte')),
            field(offset + 8, 4, L('确认号', 'Acknowledgment'), String(ack), L('期望收到的下一个序号', 'Next sequence number expected')),
            field(offset + 12, 1, L('首部长度', 'Header length'), `${dataOffset} B`, L('数据偏移 × 4 字节', 'Data offset × 4 bytes')),
            field(offset + 13, 1, L('标志', 'Flags'), flags.join(' ') || '—', L('TCP 控制位：SYN 建连、ACK 确认、FIN 断开、RST 重置、PSH 推送、URG 紧急', 'TCP control bits: SYN open, ACK acknowledge, FIN close, RST reset, PSH push, URG urgent')),
            field(offset + 14, 2, L('窗口', 'Window'), `${u16(b, offset + 14)} B`, L('接收窗口，用于流量控制', 'Receive window for flow control')),
            field(offset + 16, 2, L('校验和', 'Checksum'), `0x${hex4(u16(b, offset + 16))}`, L('覆盖 TCP 首部与负载（含伪首部）', 'Covers TCP header and payload with pseudo-header')),
            field(offset + 18, 2, L('紧急指针', 'Urgent pointer'), String(u16(b, offset + 18)), L('URG 置位时指向紧急数据', 'Points to urgent data when URG is set')),
        ];
        if (dataOffset > 20 && offset + dataOffset <= end) {
            fields.push(field(offset + 20, dataOffset - 20, L('选项', 'Options'), hexOfBytes(b, offset + 20, Math.min(20, dataOffset - 20)), L('TCP 选项：MSS、窗口缩放、SACK、时间戳等', 'TCP options: MSS, window scale, SACK, timestamps')));
        }
        const payloadOffset = offset + Math.max(20, dataOffset);
        fields.push(field(payloadOffset, Math.max(0, end - payloadOffset), L('负载长度', 'Payload length'), `${Math.max(0, end - payloadOffset)} B`, L('TCP 负载字节数', 'TCP payload byte count')));
        addLayer('TCP', fields);
        if (dataOffset >= 20)
            decodeApp(payloadOffset, end, 'tcp', srcPort, dstPort);
    }
    function decodeUdp(offset, end) {
        if (offset + 8 > end) {
            warnings.push(L('UDP 首部不完整，无法继续解析。', 'UDP header is truncated.'));
            return;
        }
        const srcPort = u16(b, offset);
        const dstPort = u16(b, offset + 2);
        const length = u16(b, offset + 4);
        const payloadEnd = length >= 8 ? Math.min(end, offset + length) : end;
        if (length < 8)
            warnings.push(L('UDP 长度字段小于 8，报文可能已损坏。', 'UDP length is below 8; the packet may be corrupt.'));
        addLayer('UDP', [
            field(offset, 2, L('源端口', 'Source port'), serviceLabel(srcPort), L('发送方 UDP 端口', 'Sender UDP port')),
            field(offset + 2, 2, L('目的端口', 'Destination port'), serviceLabel(dstPort), L('接收方 UDP 端口', 'Receiver UDP port')),
            field(offset + 4, 2, L('长度', 'Length'), `${length} B`, L('UDP 首部 + 负载的总长度', 'UDP header plus payload length')),
            field(offset + 6, 2, L('校验和', 'Checksum'), `0x${hex4(u16(b, offset + 6))}`, L('覆盖 UDP 首部与负载（含伪首部）；0 表示未启用', 'Covers header and payload with pseudo-header; 0 means disabled')),
            field(offset + 8, Math.max(0, payloadEnd - offset - 8), L('负载长度', 'Payload length'), `${Math.max(0, payloadEnd - offset - 8)} B`, L('UDP 负载字节数', 'UDP payload byte count')),
        ]);
        decodeApp(offset + 8, payloadEnd, 'udp', srcPort, dstPort);
    }
    function decodeIcmp(offset, end, v6 = false) {
        if (offset + 4 > end) {
            warnings.push(L('ICMP 首部不完整。', 'ICMP header is truncated.'));
            return;
        }
        const type = b[offset];
        const code = b[offset + 1];
        const table = v6 ? ICMPV6_TYPES : ICMP_TYPES;
        const typeName = table[type] ? L(table[type].zh, table[type].en) : L('未知类型', 'Unknown type');
        const valid = v6 ? null : inetChecksum(b, offset, end) === 0;
        const checksumMeaning = v6
            ? L('ICMPv6 校验和覆盖 IPv6 伪首部，这里只显示原值', 'ICMPv6 checksum covers the IPv6 pseudo-header; raw value shown')
            : valid
                ? L('校验通过', 'Checksum valid')
                : L('校验失败，报文可能已损坏', 'Checksum mismatch; packet may be corrupt');
        const fields = [
            field(offset, 1, L('类型', 'Type'), `${type} · ${typeName}`, L('ICMP 消息类型', 'ICMP message type')),
            field(offset + 1, 1, L('代码', 'Code'), String(code), L('对类型进一步细分', 'Further qualifies the type')),
            field(offset + 2, 2, L('校验和', 'Checksum'), `0x${hex4(u16(b, offset + 2))}`, checksumMeaning),
        ];
        if ((!v6 && (type === 0 || type === 8)) || (v6 && (type === 128 || type === 129))) {
            fields.push(field(offset + 4, 2, L('标识符', 'Identifier'), String(u16(b, offset + 4)), L('用于匹配请求与应答', 'Matches request and reply')));
            fields.push(field(offset + 6, 2, L('序号', 'Sequence'), String(u16(b, offset + 6)), L('请求序号', 'Request sequence number')));
        }
        if (end > offset + 8)
            fields.push(payloadField(offset + 8, end - offset - 8, L('ICMP 附带数据', 'ICMP payload')));
        addLayer(v6 ? 'ICMPv6' : 'ICMP', fields);
    }
    function decodeArp(offset, end) {
        if (offset + 28 > end) {
            warnings.push(L('ARP 报文不完整。', 'ARP packet is truncated.'));
            return;
        }
        const opcode = u16(b, offset + 6);
        const opName = opcode === 1 ? L('请求', 'Request') : opcode === 2 ? L('应答', 'Reply') : L('未知', 'Unknown');
        addLayer('ARP', [
            field(offset, 2, L('硬件类型', 'Hardware type'), `${u16(b, offset)} · Ethernet`, L('1 表示以太网', '1 means Ethernet')),
            field(offset + 2, 2, L('协议类型', 'Protocol type'), `0x${hex4(u16(b, offset + 2))} · IPv4`, L('上层协议地址类型', 'Upper-layer address type')),
            field(offset + 4, 1, L('硬件地址长度', 'Hardware length'), `${b[offset + 4]} B`, L('MAC 地址长度', 'MAC address length')),
            field(offset + 5, 1, L('协议地址长度', 'Protocol length'), `${b[offset + 5]} B`, L('IP 地址长度', 'IP address length')),
            field(offset + 6, 2, L('操作码', 'Opcode'), `${opcode} · ${opName}`, L('1 请求解析 MAC，2 应答', '1 resolves a MAC, 2 replies')),
            field(offset + 8, 6, L('发送方 MAC', 'Sender MAC'), macOf(b, offset + 8), L('发起方硬件地址', 'Sender hardware address')),
            field(offset + 14, 4, L('发送方 IP', 'Sender IP'), ipv4Of(b, offset + 14), L('发起方 IPv4 地址', 'Sender IPv4 address')),
            field(offset + 18, 6, L('目标 MAC', 'Target MAC'), macOf(b, offset + 18), L('请求时通常为全 0', 'Usually all zero in requests')),
            field(offset + 24, 4, L('目标 IP', 'Target IP'), ipv4Of(b, offset + 24), L('要解析的 IPv4 地址', 'IPv4 address being resolved')),
        ]);
    }
    function readDnsName(start, end) {
        let pos = start;
        let next = -1;
        let jumps = 0;
        const labels = [];
        while (pos < end && jumps < 32) {
            const len = b[pos];
            if ((len & 0xc0) === 0xc0) {
                if (pos + 1 >= end)
                    return null;
                if (next < 0)
                    next = pos + 2;
                pos = ((len & 0x3f) << 8) | b[pos + 1];
                jumps++;
                continue;
            }
            if (len === 0)
                return { name: labels.join('.') || '.', next: next < 0 ? pos + 1 : next };
            if (len > 63 || pos + 1 + len > end)
                return null;
            let label = '';
            for (let i = pos + 1; i <= pos + len; i++)
                label += b[i] >= 0x20 && b[i] < 0x7f ? String.fromCharCode(b[i]) : '·';
            labels.push(label);
            pos += len + 1;
        }
        return null;
    }
    function decodeDns(offset, end, name) {
        if (offset + 12 > end) {
            warnings.push(L('DNS 报文不完整。', 'DNS message is truncated.'));
            return;
        }
        const qd = u16(b, offset + 4);
        const an = u16(b, offset + 6);
        const ns = u16(b, offset + 8);
        const ar = u16(b, offset + 10);
        const flags = u16(b, offset + 2);
        const rcode = flags & 0x0f;
        const fields = [
            field(offset, 2, L('事务 ID', 'Transaction ID'), `0x${hex4(u16(b, offset))}`, L('匹配请求与应答', 'Matches query and response')),
            field(offset + 2, 2, L('标志', 'Flags'), `0x${hex4(flags)}`, L(`QR=${(flags >> 15) & 1} Opcode=${(flags >> 11) & 0x0f} AA=${(flags >> 10) & 1} TC=${(flags >> 9) & 1} RD=${(flags >> 8) & 1} RA=${(flags >> 7) & 1} RCODE=${rcode}`, `QR=${(flags >> 15) & 1} Opcode=${(flags >> 11) & 0x0f} AA=${(flags >> 10) & 1} TC=${(flags >> 9) & 1} RD=${(flags >> 8) & 1} RA=${(flags >> 7) & 1} RCODE=${rcode}`)),
            field(offset + 4, 2, L('问题数', 'Questions'), String(qd), L('查询的问题数量', 'Number of questions')),
            field(offset + 6, 2, L('回答数', 'Answers'), String(an), L('应答资源记录数量', 'Number of answer records')),
            field(offset + 8, 2, L('授权数', 'Authority'), String(ns), L('授权资源记录数量', 'Number of authority records')),
            field(offset + 10, 2, L('附加数', 'Additional'), String(ar), L('附加资源记录数量', 'Number of additional records')),
        ];
        let pos = offset + 12;
        const questionCount = Math.min(qd, 20);
        for (let i = 0; i < questionCount && pos < end; i++) {
            const parsed = readDnsName(pos, end);
            if (!parsed)
                break;
            const qtype = u16(b, parsed.next);
            const qclass = u16(b, parsed.next + 2);
            fields.push(field(pos, parsed.next - pos, `${L('问题', 'Question')} ${i + 1}`, parsed.name, L('查询域名', 'Queried domain name')));
            if (parsed.next + 4 <= end) {
                fields.push(field(parsed.next, 2, L('查询类型', 'Query type'), `${qtype} · ${DNS_TYPES[qtype] ?? '?'}`, L('A=IPv4、AAAA=IPv6、CNAME=别名等', 'A=IPv4, AAAA=IPv6, CNAME=alias, etc.')));
                fields.push(field(parsed.next + 2, 2, L('查询类', 'Query class'), String(qclass), L('1 表示 Internet (IN)', '1 means Internet (IN)')));
            }
            pos = parsed.next + 4;
        }
        const answerCount = Math.min(an, 50);
        for (let i = 0; i < answerCount && pos < end; i++) {
            const parsed = readDnsName(pos, end);
            if (!parsed || parsed.next + 10 > end)
                break;
            const type = u16(b, parsed.next);
            const cls = u16(b, parsed.next + 2);
            const ttl = u32(b, parsed.next + 4);
            const rdlen = u16(b, parsed.next + 8);
            const rdOffset = parsed.next + 10;
            if (rdOffset + rdlen > end)
                break;
            let value = hexOfBytes(b, rdOffset, Math.min(rdlen, 16));
            if (type === 1 && rdlen === 4)
                value = ipv4Of(b, rdOffset);
            else if (type === 28 && rdlen === 16)
                value = ipv6Of(b, rdOffset);
            else if (type === 2 || type === 5 || type === 12)
                value = readDnsName(rdOffset, end)?.name ?? value;
            else if (type === 15 && rdlen >= 3) {
                const mx = readDnsName(rdOffset + 2, end)?.name;
                value = `${u16(b, rdOffset)}${mx ? ` → ${mx}` : ''}`;
            }
            else if (type === 16 && rdlen > 0) {
                let txt = '';
                let p = rdOffset;
                while (p < rdOffset + rdlen) {
                    const len = b[p];
                    if (len === 0 || p + 1 + len > end)
                        break;
                    txt += printable(b, p + 1, len, len);
                    p += len + 1;
                }
                value = txt;
            }
            fields.push(field(pos, rdOffset + rdlen - pos, `${L('回答', 'Answer')} ${i + 1} · ${DNS_TYPES[type] ?? type}`, `${parsed.name} → ${value}`, L(`类型 ${type}，类 ${cls}，TTL ${ttl} 秒`, `Type ${type}, class ${cls}, TTL ${ttl}s`)));
            pos = rdOffset + rdlen;
        }
        addLayer(name, fields, L(`问题 ${qd} · 回答 ${an}`, `${qd} questions · ${an} answers`));
    }
    function decodeHttp(offset, end) {
        const lines = [];
        let pos = offset;
        while (pos < end && lines.length < 80) {
            let lineEnd = pos;
            while (lineEnd < end && b[lineEnd] !== 0x0a)
                lineEnd++;
            const textEnd = lineEnd > pos && b[lineEnd - 1] === 0x0d ? lineEnd - 1 : lineEnd;
            let text = '';
            for (let i = pos; i < textEnd; i++)
                text += b[i] >= 0x20 && b[i] < 0x7f ? String.fromCharCode(b[i]) : '·';
            lines.push({ text, start: pos, end: textEnd });
            if (text === '' && lines.length > 1) {
                pos = lineEnd + 1;
                break;
            }
            pos = lineEnd + 1;
        }
        if (!lines.length)
            return;
        const fields = [];
        const first = lines[0];
        const isRequest = /^(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH|TRACE|CONNECT) /.test(first.text);
        fields.push(field(first.start, first.end - first.start, isRequest ? L('请求行', 'Request line') : L('状态行', 'Status line'), first.text, isRequest
            ? L('方法 + 路径 + HTTP 版本', 'Method, path and HTTP version')
            : L('HTTP 版本 + 状态码 + 原因短语', 'HTTP version, status code and reason phrase')));
        const headerMeaning = {
            host: { zh: '请求的目标主机（虚拟主机路由）', en: 'Target host for virtual-host routing' },
            'user-agent': { zh: '客户端软件标识', en: 'Client software identifier' },
            'content-type': { zh: '正文的媒体类型与字符集', en: 'Body media type and charset' },
            'content-length': { zh: '正文字节数', en: 'Body length in bytes' },
            accept: { zh: '客户端可接受的响应类型', en: 'Response media types the client accepts' },
            connection: { zh: '连接管理策略（keep-alive / close）', en: 'Connection handling (keep-alive / close)' },
            cookie: { zh: '客户端携带的 Cookie（敏感信息）', en: 'Cookies sent by the client (sensitive)' },
            'set-cookie': { zh: '服务器下发的 Cookie（敏感信息）', en: 'Cookie issued by the server (sensitive)' },
            authorization: { zh: '认证凭据（敏感信息，勿外传）', en: 'Authorization credentials (sensitive)' },
            server: { zh: '服务端软件标识', en: 'Server software identifier' },
            date: { zh: '消息生成时间', en: 'Message creation time' },
            location: { zh: '重定向目标地址', en: 'Redirect target' },
        };
        for (const line of lines.slice(1)) {
            if (!line.text)
                continue;
            const colon = line.text.indexOf(':');
            if (colon <= 0)
                continue;
            const name = line.text.slice(0, colon).trim();
            const value = line.text.slice(colon + 1).trim();
            const known = headerMeaning[name.toLowerCase()];
            fields.push(field(line.start, line.end - line.start, name, value, known ? L(known.zh, known.en) : L('HTTP 头部字段', 'HTTP header field')));
        }
        if (pos < end)
            fields.push(payloadField(pos, end - pos, L('HTTP 正文', 'HTTP body')));
        addLayer('HTTP', fields);
    }
    function decodeTls(offset, end) {
        if (offset + 5 > end)
            return;
        const type = b[offset];
        const version = u16(b, offset + 1);
        const length = u16(b, offset + 3);
        const typeNames = {
            20: L('变更密码规格', 'Change cipher spec'),
            21: L('告警', 'Alert'),
            22: L('握手', 'Handshake'),
            23: L('应用数据', 'Application data'),
        };
        const versionNames = { 0x0301: 'TLS 1.0', 0x0302: 'TLS 1.1', 0x0303: 'TLS 1.2', 0x0304: 'TLS 1.3' };
        const fields = [
            field(offset, 1, L('内容类型', 'Content type'), `${type} · ${typeNames[type] ?? '?'}`, L('TLS 记录承载的内容类型', 'Payload type carried by the TLS record')),
            field(offset + 1, 2, L('协议版本', 'Version'), `0x${hex4(version)} · ${versionNames[version] ?? '?'}`, L('记录层协议版本', 'Record-layer protocol version')),
            field(offset + 3, 2, L('记录长度', 'Record length'), `${length} B`, L('后续 TLS 记录字节数', 'Bytes following in this TLS record')),
        ];
        if (offset + 5 + length > end)
            warnings.push(L('TLS 记录长度超过报文长度，可能被截断。', 'TLS record length exceeds the packet; it may be truncated.'));
        addLayer('TLS', fields);
    }
    function decodeIPv4(offset, end) {
        if (offset + 20 > end) {
            warnings.push(L('IPv4 首部不完整。', 'IPv4 header is truncated.'));
            return;
        }
        const ihl = (b[offset] & 0x0f) * 4;
        if (ihl < 20 || offset + ihl > end) {
            warnings.push(L('IPv4 首部长度非法。', 'IPv4 header length is invalid.'));
            return;
        }
        const total = u16(b, offset + 2);
        const declaredEnd = total >= ihl ? offset + total : end;
        const payloadEnd = Math.min(end, declaredEnd);
        if (declaredEnd > end)
            warnings.push(L(`IPv4 总长度 ${total} 字节超过实际数据，报文可能被截断。`, `IPv4 total length ${total} exceeds captured data; the packet may be truncated.`));
        const flagsFrag = u16(b, offset + 6);
        const mf = (flagsFrag >> 13) & 1;
        const df = (flagsFrag >> 14) & 1;
        const reserved = (flagsFrag >> 15) & 1;
        const fragOffset = flagsFrag & 0x1fff;
        const proto = b[offset + 9];
        const checksumOk = inetChecksum(b, offset, offset + ihl) === 0;
        const fields = [
            field(offset, 1, L('版本与首部长度', 'Version & IHL'), `v${b[offset] >> 4} · ${ihl} B`, L('高 4 位版本，低 4 位首部长度 × 4 字节', 'High nibble is version; low nibble is header length × 4 bytes')),
            field(offset + 1, 1, L('服务类型', 'DSCP / ECN'), `0x${hex2(b[offset + 1])}`, L(`DSCP ${b[offset + 1] >> 2}，ECN ${b[offset + 1] & 3}`, `DSCP ${b[offset + 1] >> 2}, ECN ${b[offset + 1] & 3}`)),
            field(offset + 2, 2, L('总长度', 'Total length'), `${total} B`, L('IPv4 首部 + 负载的总字节数', 'IPv4 header plus payload byte count')),
            field(offset + 4, 2, L('标识', 'Identification'), `0x${hex4(u16(b, offset + 4))}`, L('用于重组分片', 'Used to reassemble fragments')),
            field(offset + 6, 2, L('标志与分片', 'Flags & fragment'), `DF=${df} MF=${mf} 偏移=${fragOffset}`, L(`保留位=${reserved}，DF 禁止分片，MF 后续还有分片，片偏移 × 8 字节`, `Reserved=${reserved}, DF don't fragment, MF more fragments, offset × 8 bytes`)),
            field(offset + 8, 1, L('TTL', 'TTL'), String(b[offset + 8]), L('每经过一个路由器减 1，为 0 时丢弃', 'Decremented by each router; dropped at 0')),
            field(offset + 9, 1, L('协议', 'Protocol'), protocolLabel(proto), L('上层协议：6=TCP、17=UDP、1=ICMP', 'Upper protocol: 6=TCP, 17=UDP, 1=ICMP')),
            field(offset + 10, 2, L('首部校验和', 'Header checksum'), `0x${hex4(u16(b, offset + 10))}`, checksumOk ? L('校验通过', 'Checksum valid') : L('校验失败，首部可能已损坏', 'Checksum mismatch; header may be corrupt')),
            field(offset + 12, 4, L('源 IP', 'Source IP'), ipv4Of(b, offset + 12), L('发送方 IPv4 地址', 'Sender IPv4 address')),
            field(offset + 16, 4, L('目的 IP', 'Destination IP'), ipv4Of(b, offset + 16), L('接收方 IPv4 地址', 'Receiver IPv4 address')),
        ];
        if (ihl > 20)
            fields.push(field(offset + 20, ihl - 20, L('选项', 'Options'), hexOfBytes(b, offset + 20, Math.min(24, ihl - 20)), L('IPv4 可选项（记录路由、时间戳等）', 'IPv4 options such as record route or timestamps')));
        addLayer('IPv4', fields);
        if (fragOffset > 0) {
            warnings.push(L('这是后续分片，不含 TCP/UDP 首部；需要先重组再解析传输层。', 'This is a later fragment without a TCP/UDP header; reassemble before decoding transport.'));
            if (payloadEnd > offset + ihl)
                addLayer(L('IP 分片数据', 'IP fragment data'), [payloadField(offset + ihl, payloadEnd - offset - ihl, L('分片负载', 'Fragment payload'))]);
            return;
        }
        if (mf)
            warnings.push(L('IPv4 报文被分片，这里只解析了第一片。', 'IPv4 packet is fragmented; only the first fragment is decoded.'));
        const payloadOffset = offset + ihl;
        if (payloadOffset >= payloadEnd)
            return;
        if (proto === 6)
            decodeTcp(payloadOffset, payloadEnd);
        else if (proto === 17)
            decodeUdp(payloadOffset, payloadEnd);
        else if (proto === 1)
            decodeIcmp(payloadOffset, payloadEnd);
        else if (proto === 58)
            decodeIcmp(payloadOffset, payloadEnd, true);
        else
            addLayer(L('IP 负载', 'IP payload'), [payloadField(payloadOffset, payloadEnd - payloadOffset, L(`协议 ${protocolLabel(proto)}，未继续拆解`, `Protocol ${protocolLabel(proto)} is not decoded further`))]);
    }
    function decodeIPv6(offset, end) {
        if (offset + 40 > end) {
            warnings.push(L('IPv6 首部不完整。', 'IPv6 header is truncated.'));
            return;
        }
        const payloadLength = u16(b, offset + 4);
        const payloadEnd = payloadLength > 0 ? Math.min(end, offset + 40 + payloadLength) : end;
        if (payloadLength > 0 && offset + 40 + payloadLength > end)
            warnings.push(L('IPv6 负载长度超过实际数据，报文可能被截断。', 'IPv6 payload length exceeds captured data; the packet may be truncated.'));
        const traffic = ((b[offset] & 0x0f) << 4) | (b[offset + 1] >> 4);
        const flow = ((b[offset + 1] & 0x0f) << 16) | (b[offset + 2] << 8) | b[offset + 3];
        let next = b[offset + 6];
        addLayer('IPv6', [
            field(offset, 1, L('版本 / 流量类别', 'Version / traffic class'), `v${b[offset] >> 4} · ${traffic}`, L('版本 6；流量类别用于区分服务', 'Version 6; traffic class for differentiated services')),
            field(offset + 1, 3, L('流标签', 'Flow label'), `0x${flow.toString(16).toUpperCase()}`, L('同一数据流的标识', 'Identifies packets in the same flow')),
            field(offset + 4, 2, L('负载长度', 'Payload length'), `${payloadLength} B`, L('IPv6 首部之后的字节数', 'Bytes following the IPv6 header')),
            field(offset + 6, 1, L('下一个首部', 'Next header'), protocolLabel(next), L('传输层协议或扩展头', 'Transport protocol or extension header')),
            field(offset + 7, 1, L('跳数限制', 'Hop limit'), String(b[offset + 7]), L('每经过一个路由器减 1', 'Decremented by each router')),
            field(offset + 8, 16, L('源地址', 'Source address'), ipv6Of(b, offset + 8), L('发送方 IPv6 地址', 'Sender IPv6 address')),
            field(offset + 24, 16, L('目的地址', 'Destination address'), ipv6Of(b, offset + 24), L('接收方 IPv6 地址', 'Receiver IPv6 address')),
        ]);
        let pos = offset + 40;
        let headers = 0;
        while (headers < 8 && pos + 2 <= payloadEnd) {
            if (!(next in EXT_HEADERS))
                break;
            const extName = EXT_HEADERS[next];
            const len = next === 44 ? 8 : next === 51 ? (b[pos + 1] + 2) * 4 : (b[pos + 1] + 1) * 8;
            if (len <= 0 || pos + len > payloadEnd) {
                warnings.push(L(`IPv6 扩展头 ${extName} 长度非法。`, `IPv6 extension header ${extName} has an invalid length.`));
                return;
            }
            const nextHeader = b[pos];
            const fields = [
                field(pos, 1, L('下一个首部', 'Next header'), protocolLabel(nextHeader), L('扩展头之后的协议', 'Protocol after this extension header')),
                field(pos + 1, 1, L('扩展头长度', 'Header length'), `${len} B`, L(`${extName} 的长度`, `${extName} length`)),
            ];
            if (next === 44) {
                const frag = u16(b, pos + 2);
                fields.push(field(pos + 2, 2, L('分片信息', 'Fragment info'), `偏移=${(frag >> 3) & 0x1fff} · M=${frag & 1}`, L('M=1 表示后面还有分片', 'M=1 means more fragments follow')));
            }
            addLayer(`IPv6 · ${extName}`, fields);
            next = nextHeader;
            pos += len;
            headers++;
        }
        if (pos >= payloadEnd)
            return;
        if (next === 6)
            decodeTcp(pos, payloadEnd);
        else if (next === 17)
            decodeUdp(pos, payloadEnd);
        else if (next === 58)
            decodeIcmp(pos, payloadEnd, true);
        else
            addLayer(L('IPv6 负载', 'IPv6 payload'), [payloadField(pos, payloadEnd - pos, L(`下一个首部 ${protocolLabel(next)}，未继续拆解`, `Next header ${protocolLabel(next)} is not decoded further`))]);
    }
    function decodeEthernet(offset) {
        const ethertype = u16(b, offset + 12);
        addLayer('Ethernet II', [
            field(offset, 6, L('目的 MAC', 'Destination MAC'), macOf(b, offset), L('接收该帧的网卡硬件地址', 'Hardware address of the receiving NIC')),
            field(offset + 6, 6, L('源 MAC', 'Source MAC'), macOf(b, offset + 6), L('发送该帧的网卡硬件地址', 'Hardware address of the sending NIC')),
            field(offset + 12, 2, L('类型', 'EtherType'), `0x${hex4(ethertype)} · ${ETHERTYPES[ethertype] ?? '?'}`, L('指明上层协议', 'Identifies the upper-layer protocol')),
        ]);
        kind = 'ethernet';
        let pos = offset + 14;
        let type = ethertype;
        while ((type === 0x8100 || type === 0x88a8) && pos + 4 <= b.length) {
            const tci = u16(b, pos);
            const inner = u16(b, pos + 2);
            addLayer(type === 0x8100 ? '802.1Q VLAN' : '802.1ad QinQ', [
                field(pos, 2, L('优先级与 VLAN ID', 'PCP / DEI / VID'), `PCP=${tci >> 13} DEI=${(tci >> 12) & 1} VID=${tci & 0x0fff}`, L('VLAN 标签：优先级、丢弃指示、虚拟局域网 ID', 'VLAN tag: priority, drop eligible, VLAN ID')),
                field(pos + 2, 2, L('内层类型', 'Inner EtherType'), `0x${hex4(inner)} · ${ETHERTYPES[inner] ?? '?'}`, L('VLAN 标签之后的上层协议', 'Protocol after the VLAN tag')),
            ]);
            type = inner;
            pos += 4;
        }
        if (type === 0x0800)
            decodeIPv4(pos, b.length);
        else if (type === 0x86dd)
            decodeIPv6(pos, b.length);
        else if (type === 0x0806)
            decodeArp(pos, b.length);
        else if (pos < b.length) {
            addLayer(L('以太网负载', 'Ethernet payload'), [payloadField(pos, b.length - pos, L(`EtherType 0x${hex4(type)} 未继续拆解`, `EtherType 0x${hex4(type)} is not decoded further`))]);
        }
    }
    if (isEthernet(b))
        decodeEthernet(0);
    else if (isIPv4(b)) {
        kind = 'ipv4';
        decodeIPv4(0, b.length);
    }
    else if (isIPv6(b)) {
        kind = 'ipv6';
        decodeIPv6(0, b.length);
    }
    else if (isArp(b)) {
        kind = 'arp';
        decodeArp(0, b.length);
    }
    else {
        warnings.push(L('未识别出已知网络协议，已按原始字节展示。', 'No known network protocol detected; showing raw bytes.'));
        if (b.length) {
            addLayer(L('原始数据', 'Raw data'), [
                field(0, b.length, L('长度', 'Length'), `${b.length} B`, L('报文总字节数', 'Total packet byte count')),
                payloadField(0, b.length, L('未匹配以太网 / IPv4 / IPv6 / ARP 头', 'No Ethernet / IPv4 / IPv6 / ARP header matched')),
            ]);
        }
    }
    const format = stack.length ? stack.join(' › ') : L('未知格式', 'Unknown');
    return { format, kind, summary: format, layers, warnings };
}
