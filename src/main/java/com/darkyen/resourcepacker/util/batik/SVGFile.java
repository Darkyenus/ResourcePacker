package com.darkyen.resourcepacker.util.batik;

import com.esotericsoftware.minlog.Log;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.anim.dom.SVGOMDocument;
import org.apache.batik.bridge.*;
import org.apache.batik.bridge.svg12.SVG12BridgeContext;
import org.apache.batik.dom.util.DOMUtilities;
import org.apache.batik.ext.awt.image.GraphicsUtil;
import org.apache.batik.gvt.CanvasGraphicsNode;
import org.apache.batik.gvt.CompositeGraphicsNode;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.renderer.ConcreteImageRendererFactory;
import org.apache.batik.gvt.renderer.ImageRenderer;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.SVGConstants;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.svg.SVGSVGElement;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Simple SVG manipulation API created by gutting {@link org.apache.batik.transcoder.image.ImageTranscoder}
 * and its subclasses.
 */
public class SVGFile {

    private static final String LOG = "SVGFile";

    private static final String XML_PARSER_CLASS_NAME = XMLResourceDescriptor.getXMLParserClassName();
    private static final boolean XML_PARSER_VALIDATING = false;

    /**
     * Transform needed to render the current area of interest
     */
    private AffineTransform curTxf;

    private final BridgeContext ctx;

    private final ParsedURL url;

    private final SVGSVGElement root;

    private final GraphicsNode gvtRoot;

    /**
     * Image's original width and height.
     */
    public final double docWidth, docHeight;

    private SVGOMDocument createDocument(String uri, ParsedURL url, InputStream input) throws IOException {
        final SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(XML_PARSER_CLASS_NAME);
        f.setValidating(XML_PARSER_VALIDATING);

        Document document = f.createDocument(uri, input);

        if ((document != null) && !(document.getImplementation() instanceof SVGDOMImplementation)) {
            document = DOMUtilities.deepCloneDocument(document, SVGDOMImplementation.getDOMImplementation());
            if (url != null) {
                ((SVGOMDocument)document).setParsedURL(url);
            }
        }

        if (document == null) {
            throw new NullPointerException("Document created from uri "+url+" is null");
        }

        return (SVGOMDocument) document;
    }

    public SVGFile(String uri, InputStream input) throws Exception {
        final ParsedURL url = uri == null ? null : new ParsedURL(uri);


        final SVGOMDocument svgDoc = createDocument(uri, url, input);
        final BridgeContext ctx;
        /* The user agent dedicated to an SVG Transcoder. */
        UserAgent userAgent = new SVGFileUserAgent();
        if (svgDoc.isSVG12()) {
            ctx = new SVG12BridgeContext(userAgent);
        } else {
            ctx = new BridgeContext(userAgent);
        }

        this.url = url;
        this.ctx = ctx;
        this.root = svgDoc.getRootElement();
        this.gvtRoot = new GVTBuilder().build(ctx, svgDoc);
        this.docWidth = ctx.getDocumentSize().getWidth();
        this.docHeight = ctx.getDocumentSize().getHeight();
    }

    public BufferedImage rasterize(int rasterizeWidth, int rasterizeHeight, Color background) throws Exception {

        {
            // compute the preserveAspectRatio matrix
            AffineTransform Px;

            {
                String ref = this.url == null ? null : this.url.getRef();

                // XXX Update this to use the animated value of 'viewBox' and
                //     'preserveAspectRatio'.
                String viewBox = this.root.getAttributeNS(null, SVGConstants.SVG_VIEW_BOX_ATTRIBUTE);

                if ((ref != null) && (ref.length() != 0)) {
                    Px = ViewBox.getViewTransform(ref, this.root, rasterizeWidth, rasterizeHeight, this.ctx);
                } else if ((viewBox != null) && (viewBox.length() != 0)) {
                    String aspectRatio = this.root.getAttributeNS(null, SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE);
                    Px = ViewBox.getPreserveAspectRatioTransform(this.root, viewBox, aspectRatio, rasterizeWidth, rasterizeHeight, ctx);
                } else {
                    // no viewBox has been specified, create a scale transform
                    final double xScale = rasterizeWidth/docWidth;
                    final double yScale = rasterizeHeight/docHeight;
                    final double scale = Math.min(xScale,yScale);
                    Px = AffineTransform.getScaleInstance(scale, scale);
                }
            }

            CanvasGraphicsNode cgn = getCanvasGraphicsNode(this.gvtRoot);
            if (cgn != null) {
                cgn.setViewingTransform(Px);
                this.curTxf = new AffineTransform();
            } else {
                this.curTxf = Px;
            }
        }


        final ImageRenderer renderer = new ConcreteImageRendererFactory().createStaticImageRenderer();
        renderer.updateOffScreen(rasterizeWidth, rasterizeHeight);
        renderer.setTransform(this.curTxf);
        renderer.setTree(this.gvtRoot);
        renderer.repaint(curTxf.createInverse().createTransformedShape(new Rectangle2D.Float(0, 0, rasterizeWidth, rasterizeHeight)));

        final BufferedImage dest = new BufferedImage(rasterizeWidth, rasterizeHeight, BufferedImage.TYPE_INT_ARGB);

        final BufferedImage offScreenImage = renderer.getOffScreen();
        Graphics2D g2d = GraphicsUtil.createGraphics(dest);
        if (background != null) {
            g2d.setColor(background);
            g2d.fillRect(0, 0, rasterizeWidth, rasterizeHeight);
        }
        if (offScreenImage != null) { // might be null if the svg document is empty
            g2d.drawRenderedImage(offScreenImage, new AffineTransform());
        }
        g2d.dispose();

        return dest;
    }


    public void dispose() {
        if (ctx != null) {
            ctx.dispose();
        }
    }

    protected CanvasGraphicsNode getCanvasGraphicsNode(GraphicsNode gn) {
        if (!(gn instanceof CompositeGraphicsNode))
            return null;
        CompositeGraphicsNode cgn = (CompositeGraphicsNode)gn;
        List children = cgn.getChildren();
        if (children.size() == 0)
            return null;
        gn = (GraphicsNode)children.get(0);
        if (!(gn instanceof CanvasGraphicsNode))
            return null;
        return (CanvasGraphicsNode)gn;
    }

    private final class SVGFileUserAgent extends UserAgentAdapter {

        private SVGFileUserAgent() {
            addStdFeatures();
        }

        public AffineTransform getTransform() {
            return curTxf;
        }

        public void setTransform(AffineTransform at) {
            curTxf = at;
        }

        public Dimension2D getViewportSize() {
            return new Dimension((int)docWidth, (int)docHeight);
        }

        public void displayError(String message) {
            Log.error(LOG, "SVGFileUserAgent error: "+message);
        }

        public void displayError(Exception e) {
            Log.error(LOG, "SVGFileUserAgent error", e);
        }

        public void displayMessage(String message) {
            Log.info(LOG, "SVGFileUserAgent message: "+message);
        }

        public String getMedia() {
            return "screen";
        }

        public String getXMLParserClassName() {
            return XML_PARSER_CLASS_NAME;
        }

        public boolean isXMLParserValidating() {
            return XML_PARSER_VALIDATING;
        }

        public ScriptSecurity getScriptSecurity(String scriptType, ParsedURL scriptPURL, ParsedURL docPURL){
            return new NoLoadScriptSecurity(scriptType);
        }
    }
}
