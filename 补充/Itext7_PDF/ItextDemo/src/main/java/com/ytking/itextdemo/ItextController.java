package com.ytking.itextdemo;

import com.alibaba.fastjson.JSON;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.utils.PageRange;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.kernel.utils.PdfSplitter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.Style;
import com.itextpdf.layout.element.*;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import static com.ytking.itextdemo.PathUtils.getAbsolutePathWithProject;

/**
 * @author 应涛
 * @date 2022/2/11
 * @function：
 */
@RestController
@RequestMapping("/itex")
public class ItextController {
    //文件存放路径--PATH = E:\banyun\javaInterview\补充\Itext7_PDF\ItextDemo
    final String PATH = getAbsolutePathWithProject();
    //使用系统本地字体，可以解决生成的pdf中无法显示中文问题，本处字体为宋体
    //在创建字体时直接使用即可解决中文问题
//    PdfFont sysFont = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H", true);

    public ItextController() throws IOException {
    }
    /**输入示例：
     {
     "regTime":"2018-8-17",
     "regType":"有限责任公司（非自然人投资或控股的法人独资）",
     "resourceFrom":"无",
     "regCapital":"35000.0万人名币",
     "belongsTo":"制造业",
     "enterpriceScale":"2亿元以上~4亿元（含）",
     "adminRegion":"省和自治区/浙江省/湖州市",
     "postCode":"313216",
     "collectMethod":"check",
     "isListed":"n",
     "isHighZones":"y",
     "highZonesName":"湖州莫干山高新技术产业开发区"
     }
     */
    @PostMapping("/test1")
    public String test1(@RequestBody EnterpriceRegDao entity) throws IOException {
        PdfFont sysFont = PdfFontFactory.createFont("C:/Windows/Fonts/simsun.ttc,0", PdfEncodings.IDENTITY_H);
        try {
            //Initialize PDF document
            PdfReader reader = new PdfReader("C:\\Users\\应涛\\Desktop\\mode.pdf");
            PdfWriter writer = new PdfWriter("C:\\Users\\应涛\\Desktop\\mode1.pdf");
            PdfDocument pdf = new PdfDocument(reader, writer);
            PdfAcroForm form = PdfAcroForm.getAcroForm(pdf, true);
            Map<String, PdfFormField> fields = form.getFormFields();
//            EnterpriceRegDao entity = new EnterpriceRegDao();
//            //测试数据
//            entity.setEnterpriceName("埃里\n巴巴");
//            entity.setRegTime("2021-1-2");
//            entity.setRegType("好啊实打实的大三等我啊我的钱权威的权威的阿萨的");
//            entity.setResourceFrom("芜湖d啊实打实大三大苏打实打实撒旦阿萨撒旦");
//            entity.setDirectorOfTax("state");
//            entity.setCollectMethod("check");
//            entity.setIsHighZones("n");
//            entity.setIsListed("y");
            Map<String, String> map = JSON.parseObject(JSON.toJSONString(entity), Map.class);
            //填充文本域
            for (Map.Entry<String, String> entry : map.entrySet()) {
                System.out.println("fields.get(entry.getKey()) = " + fields.get(entry.getKey()));
                if(null != fields.get(entry.getKey())){
                    fields.get(entry.getKey()).setFont(sysFont).setValue(entry.getValue()).setFontSize(11);
                }
                float strWidth = sysFont.getWidth(entry.getValue(), 12);
                System.out.println("entry.getValue() = " + entry.getValue()+" strWidth= "+strWidth);
                System.out.println("entry.getKey() = " + entry.getKey());
            }
            //返回值：[158.709 623.76 287.589 659.4 ]左上角x，左上角y，宽，高。
            PdfArray position = fields.get("fill_7").getWidgets().get(0).getRectangle();
            form.flattenFields();//设置表单域不可编辑
            pdf.close();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "success";
    }


    /**
     * 功能描述:
     * 测试输出，解决中文不显示问题
     *
     * @param
     * @return java.lang.String
     * @author yt
     * @date 2022/2/12 20:19
     */
    @PostMapping("/test")
    public String test() throws IOException {
        PdfFont sysFont = PdfFontFactory.createFont("C:/Windows/Fonts/simsun.ttc,0", PdfEncodings.IDENTITY_H);
        //创建基础模块
        PdfWriter writer = new PdfWriter(PATH + "\\test.pdf");
        PdfDocument pdf = new PdfDocument(writer);
        //document就是我们的pdf对象
        Document document = new Document(pdf);
        document.add(new Paragraph("中文输出测试!").setFont(sysFont));
        document.close();
        return "success";
    }

    /**
     * 功能描述:
     * list测试
     *
     * @param
     * @return java.lang.String
     * @author yt
     * @date 2022/2/12 21:07
     */
    @PostMapping("/list")
    public String list() throws IOException {
        PdfFont sysFont = PdfFontFactory.createFont("C:/Windows/Fonts/simsun.ttc,0", PdfEncodings.IDENTITY_H);
        PdfWriter writer = new PdfWriter(PATH + "\\list.pdf");
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        document.add(new Paragraph("iText list:").setFont(sysFont));
        // 创建list，位于itextpdf.layout包下
        List list = new List()
                .setSymbolIndent(12)//列表项的缩进单元
                .setListSymbol("\u2022")//列表符号，没有找到具体说明，有需求只能一个一个试了
                .setFont(sysFont);
        // 添加数据
        list.add(new ListItem("English test1"))
                .add(new ListItem("English test2"))
                .add(new ListItem("中文测试1"))
                .add(new ListItem("中文测试2"));
        document.add(list);
        document.close();
        return "success";
    }

    /**
     * 功能描述:
     * 图片输出测试
     *
     * @param
     * @return java.lang.String
     * @author yt
     * @date 2022/2/12 21:53
     */
    @PostMapping("/image")
    public String image() throws IOException {
        PdfFont sysFont = PdfFontFactory.createFont("C:/Windows/Fonts/simsun.ttc,0", PdfEncodings.IDENTITY_H);
        //创建基础模块
        PdfWriter writer = new PdfWriter(PATH + "\\image.pdf");
        PdfDocument pdf = new PdfDocument(writer);
        //document就是我们的pdf对象
        Document document = new Document(pdf);
        //生成图片对象
        Image image = new Image(ImageDataFactory.create("testImage.jpg"), 200, 400, 200);
        Image image1 = new Image(ImageDataFactory.create("testImage.jpg")).setHeight(100);
        Paragraph p = new Paragraph("图片输出测试： ").setFont(sysFont)
                .add(image)
                .add(image1);
        document.add(p);
        document.close();
        return "success";
    }

    /**
     * 功能描述:
     * 按maxPageCount大小等分PDF
     *
     * @param
     * @return java.lang.String
     * @author yt
     * @date 2022/2/13 14:15
     */
    @PostMapping("/PDFSplitter1")
    public String PDFSplitter1() throws IOException {
        final String ORIG = "C:\\Users\\应涛\\Desktop\\新建文件夹1\\培养方案.pdf";

        final int maxPageCount = 5; // create a new PDF per maxPageCount pages from the original file
        PdfDocument pdfDocument = new PdfDocument(new PdfReader(new File(ORIG)));
        PdfSplitter pdfSplitter = new PdfSplitter(pdfDocument) {
            int partNumber = 1;

            @Override
            protected PdfWriter getNextPdfWriter(PageRange documentPageRange) {
                try {
                    return new PdfWriter(PATH + "/splitDocument_" + partNumber++ + ".pdf");
                } catch (final FileNotFoundException ignored) {
                    throw new RuntimeException();
                }
            }
        };
        //官方给出的示例，第二个参数是当另一个文档准备就绪时调用的事件侦听器。例如，可以在此侦听器中关闭此文档。
        pdfSplitter.splitByPageCount(maxPageCount, (pdfDoc, pageRange) -> pdfDoc.close());
        pdfDocument.close();
        return "success";
    }

    /**
     * 功能描述:
     * 自定义选取片段大小的pdf
     *
     * @param
     * @return java.lang.String
     * @author yt
     * @date 2022/2/13 14:15
     */
    @PostMapping("/PDFSplitter2")
    public String PDFSplitter2() throws IOException {
        final String ORIG = "C:\\Users\\应涛\\Desktop\\新建文件夹1\\培养方案.pdf";
        //源文档
        PdfReader pdfReader = new PdfReader(ORIG);
        PdfDocument pdf = new PdfDocument(pdfReader);
        //生成目标文档
        PdfWriter pdfWriter = new PdfWriter(PATH + "/splitedDocument" + ".pdf");
        PdfDocument outPdfDocument = new PdfDocument(pdfWriter);
        //从页数第一页开始，
        pdf.copyPagesTo(2, 3, outPdfDocument);
        //关闭
        outPdfDocument.close();
        pdfWriter.close();
        pdf.close();
        pdfReader.close();
        return "success";
    }

    /**
     * 功能描述:
     * pdf合并
     *
     * @param
     * @return java.lang.String
     * @author yt
     * @date 2022/2/13 16:07
     */
    @PostMapping("/PdfMerge")
    public String PdfMerge() throws IOException {
        final String FILE1 = "要合并的文件1路径";
        final String FILE2 = "要合并的文件2路径";
        PdfDocument pdfDocument = new PdfDocument(new PdfReader(FILE1), new PdfWriter(PATH + "/merged.pdf"));
        PdfDocument pdfDocument2 = new PdfDocument(new PdfReader(FILE2));
        //要生成的文件
        PdfMerger merger = new PdfMerger(pdfDocument);
        //要合并的文件，第二个参数是起始页，第三个参数是结束页，我这样写就是全部合并，也可以选择合并一部分
        merger.merge(pdfDocument2, 1, pdfDocument2.getNumberOfPages());
        pdfDocument2.close();
        pdfDocument.close();
        return "success";
    }

    /**
     * 功能描述:
     * pdf旋转
     *
     * @param
     * @return java.lang.String
     * @author yt
     * @date 2022/2/13 16:07
     */
    @PostMapping("/PDFRotator")
    public String PDFRotator() throws IOException {
        final int ROTATION_DEGREES = 90; //顺时针旋转角度
        final String ORIG = "C:\\Users\\应涛\\Desktop\\新建文件夹1\\培养方案.pdf";

        PdfDocument pdfDocument = new PdfDocument(new PdfReader(ORIG), new PdfWriter(PATH + "/Rotated.pdf"));

        for (int p = 1; p <= pdfDocument.getNumberOfPages(); p++) {
            PdfPage page = pdfDocument.getPage(p);
            int rotate = page.getRotation();
            if (rotate == 0) {
                page.setRotation(ROTATION_DEGREES);
            } else {
                page.setRotation((rotate + ROTATION_DEGREES) % 360);
            }
        }

        pdfDocument.close();
        return "success";
    }

}
