package com.yaoshiji.app;

import java.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLParameters;
import java.net.Socket;

/** Minimal WebDAV client using RFC 4918 methods. */
public final class WebDavClient {
    public static class Config{public final String base,user,pass,dir;public Config(String b,String u,String p,String d){base=trimSlash(b);user=u==null?"":u;pass=p==null?"":p;dir=normalizeDir(d);}}
    public static class RetentionResult{public int kept,deleted;public String warning="";public String summary(){String s="自动备份整理：保留 "+kept+" 份，删除 "+deleted+" 份";return warning.isEmpty()?s:s+"；"+warning;}}
    private static class Response{int code;Map<String,String> headers=new HashMap<>();byte[] body=new byte[0];}
    private final Config cfg;public WebDavClient(Config c){cfg=c;}

    public String test() throws Exception{Response r=request(cfg.base+"/","PROPFIND",Collections.singletonMap("Depth","0"),null,0);if(r.code==200||r.code==207||r.code==204)return "连接成功（HTTP "+r.code+"）";throw new IOException("WebDAV连接失败：HTTP "+r.code);}
    public void ensureDir() throws Exception{
        String[] parts=cfg.dir.split("/");String cur=cfg.base;for(String p:parts){if(p==null||p.trim().isEmpty())continue;cur+="/"+encodePath(p);Response check=request(cur+"/","PROPFIND",Collections.singletonMap("Depth","0"),null,0);if(check.code==404){Response mk=request(cur+"/","MKCOL",Collections.emptyMap(),null,0);if(mk.code!=201&&mk.code!=200&&mk.code!=204&&mk.code!=405)throw new IOException("创建WebDAV目录失败：HTTP "+mk.code);}else if(check.code!=200&&check.code!=207&&check.code!=204)throw new IOException("检查WebDAV目录失败：HTTP "+check.code);}
    }
    public void upload(String name,byte[] data) throws Exception{ensureDir();Map<String,String> h=new HashMap<>();h.put("Content-Type","application/zip");Response r=request(collectionUrl()+encodePath(name),"PUT",h,data,0);if(r.code!=200&&r.code!=201&&r.code!=204)throw new IOException("上传失败：HTTP "+r.code);}
    public byte[] download(String name) throws Exception{Response r=request(collectionUrl()+encodePath(name),"GET",Collections.emptyMap(),null,0);if(r.code!=200)throw new IOException("下载失败：HTTP "+r.code);return r.body;}
    public void delete(String name) throws Exception{Response r=request(collectionUrl()+encodePath(name),"DELETE",Collections.emptyMap(),null,0);if(r.code!=200&&r.code!=202&&r.code!=204&&r.code!=404)throw new IOException("删除失败：HTTP "+r.code);}
    public List<String> listBackups() throws Exception{return listByPrefixes(new String[]{"yaoshiji_auto_","yaoshiji_manual_","yaoshiji_backup_"});}
    private List<String> listByPrefixes(String[] prefixes) throws Exception{
        ensureDir();Response r=request(collectionUrl(),"PROPFIND",Collections.singletonMap("Depth","1"),null,0);if(r.code!=207&&r.code!=200)throw new IOException("读取备份列表失败：HTTP "+r.code);String xml=new String(r.body,StandardCharsets.UTF_8);ArrayList<String> out=new ArrayList<>();Matcher m=Pattern.compile("(?is)<[^>]*href[^>]*>(.*?)</[^>]*href>").matcher(xml);while(m.find()){String href=unescape(m.group(1));try{href=URLDecoder.decode(href,"UTF-8");}catch(Exception ignored){}int q=href.indexOf('?');if(q>=0)href=href.substring(0,q);while(href.endsWith("/"))href=href.substring(0,href.length()-1);int slash=href.lastIndexOf('/');String n=slash>=0?href.substring(slash+1):href;if(n.endsWith(".zip")&&startsWithAny(n,prefixes)&&!out.contains(n))out.add(n);}out.sort(Collections.reverseOrder());return out;
    }

    /**
     * GFS-lite retention for automatic backups only:
     * - keep every backup from the last 7 days
     * - then keep one newest backup per ISO-like week for 4 previous weeks
     * - then keep one newest backup per month for 12 previous months
     * Manual and legacy backups are never touched here.
     */
    public RetentionResult pruneAutoBackups() throws Exception{
        List<String> all=listByPrefixes(new String[]{"yaoshiji_auto_"});ArrayList<BackupItem> items=new ArrayList<>();for(String n:all){Date d=parseNameDate(n,"yaoshiji_auto_");if(d!=null)items.add(new BackupItem(n,d));}
        items.sort((a,b)->Long.compare(b.date.getTime(),a.date.getTime()));RetentionResult rr=new RetentionResult();if(items.isEmpty())return rr;
        long now=System.currentTimeMillis();Set<String> keep=new HashSet<>();Set<String> weeks=new HashSet<>();Set<String> months=new HashSet<>();
        Calendar current=Calendar.getInstance();current.setFirstDayOfWeek(Calendar.MONDAY);current.setMinimalDaysInFirstWeek(4);
        for(BackupItem it:items){long ageDays=Math.max(0,(now-it.date.getTime())/(24L*60*60*1000));if(ageDays<7){keep.add(it.name);continue;}
            Calendar c=Calendar.getInstance();c.setTime(it.date);c.setFirstDayOfWeek(Calendar.MONDAY);c.setMinimalDaysInFirstWeek(4);int weekAge=weeksBetween(c,current);if(weekAge>=0&&weekAge<4){String k=c.getWeekYear()+"-W"+c.get(Calendar.WEEK_OF_YEAR);if(weeks.add(k))keep.add(it.name);continue;}
            int monthAge=(current.get(Calendar.YEAR)-c.get(Calendar.YEAR))*12+(current.get(Calendar.MONTH)-c.get(Calendar.MONTH));if(monthAge>=0&&monthAge<12){String k=c.get(Calendar.YEAR)+"-"+c.get(Calendar.MONTH);if(months.add(k))keep.add(it.name);}
        }
        if(keep.isEmpty())keep.add(items.get(0).name);
        for(BackupItem it:items){if(keep.contains(it.name)){rr.kept++;continue;}try{delete(it.name);rr.deleted++;}catch(Exception e){rr.kept++;if(rr.warning.isEmpty())rr.warning="部分旧备份删除失败";}}
        return rr;
    }

    /** Explicit migration cleanup for pre-V10 legacy backups; never called automatically. */
    public int pruneLegacyKeepLatest(int keepCount) throws Exception{
        List<String> all=listByPrefixes(new String[]{"yaoshiji_backup_"});int deleted=0;for(int i=Math.max(0,keepCount);i<all.size();i++){delete(all.get(i));deleted++;}return deleted;
    }

    private static int weeksBetween(Calendar oldC,Calendar nowC){Calendar a=(Calendar)oldC.clone(),b=(Calendar)nowC.clone();a.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);b.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);zeroTime(a);zeroTime(b);return (int)Math.max(0,(b.getTimeInMillis()-a.getTimeInMillis())/(7L*24*60*60*1000));}
    private static void zeroTime(Calendar c){c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);}
    private static Date parseNameDate(String n,String prefix){try{String s=n.substring(prefix.length(),n.length()-4);return new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).parse(s);}catch(Exception e){return null;}}
    private static boolean startsWithAny(String n,String[] p){for(String x:p)if(n.startsWith(x))return true;return false;}
    private static class BackupItem{final String name;final Date date;BackupItem(String n,Date d){name=n;date=d;}}

    private Response request(String url,String method,Map<String,String> headers,byte[] body,int redirects) throws Exception{
        if(redirects>3)throw new IOException("Too many redirects");URI u=new URI(url);String scheme=u.getScheme();if(!"http".equalsIgnoreCase(scheme)&&!"https".equalsIgnoreCase(scheme))throw new IOException("WebDAV地址必须是 http:// 或 https://");String host=u.getHost();if(host==null)throw new IOException("WebDAV地址无效");int port=u.getPort()>0?u.getPort():("https".equalsIgnoreCase(scheme)?443:80);String path=u.getRawPath();if(path==null||path.isEmpty())path="/";if(u.getRawQuery()!=null)path+="?"+u.getRawQuery();
        Socket plain=new Socket();plain.connect(new InetSocketAddress(host,port),15000);plain.setSoTimeout(30000);Socket socket=plain;
        if("https".equalsIgnoreCase(scheme)){SSLSocket ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(plain,host,port,true);SSLParameters params=ssl.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");ssl.setSSLParameters(params);ssl.startHandshake();socket=ssl;}
        try{
            OutputStream out=socket.getOutputStream();StringBuilder head=new StringBuilder();head.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");head.append("Host: ").append(host);if((port!=80&&!"https".equalsIgnoreCase(scheme))||(port!=443&&"https".equalsIgnoreCase(scheme)))head.append(':').append(port);head.append("\r\nUser-Agent: YaoShiJi/1.2.1\r\nConnection: close\r\nAccept: */*\r\n");
            if(!cfg.user.isEmpty()){String a=cfg.user+":"+cfg.pass;head.append("Authorization: Basic ").append(Base64.getEncoder().encodeToString(a.getBytes(StandardCharsets.UTF_8))).append("\r\n");}
            for(Map.Entry<String,String> e:headers.entrySet())head.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");if(body!=null)head.append("Content-Length: ").append(body.length).append("\r\n");head.append("\r\n");out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));if(body!=null&&body.length>0)out.write(body);out.flush();
            InputStream in=socket.getInputStream();String status=readLine(in);if(status==null)throw new IOException("WebDAV服务器无响应");String[] sp=status.split(" ",3);if(sp.length<2)throw new IOException("无效HTTP响应："+status);Response r=new Response();r.code=Integer.parseInt(sp[1]);String line;while((line=readLine(in))!=null&&!line.isEmpty()){int c=line.indexOf(':');if(c>0)r.headers.put(line.substring(0,c).trim().toLowerCase(Locale.ROOT),line.substring(c+1).trim());}
            String te=r.headers.get("transfer-encoding"),cl=r.headers.get("content-length");if(te!=null&&te.toLowerCase(Locale.ROOT).contains("chunked"))r.body=readChunked(in);else if(cl!=null){int len=Integer.parseInt(cl.trim());r.body=readFixed(in,len);}else r.body=readToEnd(in);
            if(r.code==301||r.code==302||r.code==307||r.code==308){String loc=r.headers.get("location");if(loc!=null&&!loc.isEmpty()){URI next=u.resolve(loc);return request(next.toString(),method,headers,body,redirects+1);}}
            return r;
        }finally{try{socket.close();}catch(Exception ignored){}}
    }

    private static String readLine(InputStream in) throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int prev=-1,x;while((x=in.read())!=-1){if(prev=='\r'&&x=='\n'){byte[] a=b.toByteArray();int len=a.length>0&&a[a.length-1]=='\r'?a.length-1:a.length;return new String(a,0,len,StandardCharsets.ISO_8859_1);}b.write(x);prev=x;}if(b.size()==0)return null;return new String(b.toByteArray(),StandardCharsets.ISO_8859_1);}
    private static byte[] readFixed(InputStream in,int len) throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream(Math.max(0,len));byte[] b=new byte[8192];int left=len;while(left>0){int n=in.read(b,0,Math.min(b.length,left));if(n<0)break;out.write(b,0,n);left-=n;}return out.toByteArray();}
    private static byte[] readToEnd(InputStream in) throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toByteArray();}
    private static byte[] readChunked(InputStream in) throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();while(true){String line=readLine(in);if(line==null)break;int semi=line.indexOf(';');if(semi>=0)line=line.substring(0,semi);int size=Integer.parseInt(line.trim(),16);if(size==0){while((line=readLine(in))!=null&&!line.isEmpty()){}break;}byte[] part=readFixed(in,size);out.write(part);readLine(in);}return out.toByteArray();}
    private String collectionUrl(){return cfg.base+cfg.dir;}
    private static String trimSlash(String s){s=s==null?"":s.trim();while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;}
    private static String normalizeDir(String s){s=s==null?"":s.trim();if(s.isEmpty())s="/YaoShiJi/";if(!s.startsWith("/"))s="/"+s;if(!s.endsWith("/"))s+="/";return s;}
    private static String encodePath(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(Exception e){return s;}}
    private static String unescape(String s){return s.replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&#39;","'").replace("&quot;","\"");}
}
