package jokrey.utilities.animation.implementations.swing.pipeline;

import jokrey.utilities.animation.implementations.swing.display.AnimationJPanel;
import jokrey.utilities.animation.pipeline.AnimationDrawer;
import jokrey.utilities.animation.util.AEColor;
import jokrey.utilities.animation.util.AEImage;
import jokrey.utilities.animation.util.AEPoint;
import jokrey.utilities.animation.util.AERect;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class AnimationDrawerSwing extends AnimationDrawer {
	public AnimationJPanel p=null;

    @Override protected AERect getPanelBoundsOnScreen() {
        return new AERect(p.getLocationOnScreen().x, p.getLocationOnScreen().y, p.getWidth(), p.getHeight());
    }


    @Override public void drawImage(AEImage param, AERect drawB) {
        int w = param.getData().length;
        int h = param.getData()[0].length;
        BufferedImage ip_img = new BufferedImage( w, h, BufferedImage.TYPE_INT_ARGB );
        final int[] a = ( (DataBufferInt) ip_img.getRaster().getDataBuffer() ).getData();
        for(int i=0;i<w;i++)
            System.arraycopy(i * param.getData()[0].length, 0, a, 0, param.getData()[0].length);
    }
    public void drawImage(BufferedImage image, AERect drawB) {
        p.getLastGraphics().drawImage(image, (int)drawB.x, (int)drawB.y, (int)(drawB.x+drawB.getWidth()), (int)(drawB.y+drawB.getHeight()),
                0, 0, image.getWidth(), image.getHeight(), null);
    }

    @Override public void drawLine(AEColor param, AEPoint p1, AEPoint p2, float size) {
        p.getLastGraphics().setColor(param==null?Color.black:new Color(param.getRed(), param.getGreen(), param.getBlue()));
        if(size!=1)
            p.getLastGraphics().setStroke(new BasicStroke(size));
        p.getLastGraphics().drawLine((int)p1.x, (int)p1.y, (int)p2.x, (int)p2.y);
    }

    @Override public void fillOval(AEColor param, AERect drawB) {
        p.getLastGraphics().setColor(param==null?Color.black:new Color(param.getRed(), param.getGreen(), param.getBlue()));
        p.getLastGraphics().fillOval((int)drawB.x, (int)drawB.y, (int)drawB.getWidth(), (int)drawB.getHeight());
    }

    @Override public void fillRect(AEColor param, AERect drawB) {
        p.getLastGraphics().setColor(param==null?Color.black:new Color(param.getRed(), param.getGreen(), param.getBlue()));
        p.getLastGraphics().fillRect((int)drawB.x, (int)drawB.y, (int)drawB.getWidth(), (int)drawB.getHeight());
    }

    @Override public double drawString(AEColor param, double font_size, String str, double mid_x, double mid_y) {
        p.getLastGraphics().setColor(param==null?Color.black:new Color(param.getRed(), param.getGreen(), param.getBlue()));
        p.getLastGraphics().setFont(new Font("Arial", Font.BOLD, (int) font_size));
        p.getLastGraphics().drawString(str, (int)(mid_x - p.getLastGraphics().getFontMetrics().stringWidth(str)/2), (int)mid_y+p.getLastGraphics().getFontMetrics().getHeight()/4);
        return p.getLastGraphics().getFontMetrics().getHeight();
    }

    @Override public void drawString(AEColor param, String str, AERect rect) {
        p.getLastGraphics().setColor(param==null?Color.black:new Color(param.getRed(), param.getGreen(), param.getBlue()));
        Graphics2D g2 = p.getLastGraphics();
        float fontSize = 20.0f;
        Font font = g2.getFont().deriveFont(fontSize);
        int width = g2.getFontMetrics(font).stringWidth(str);
        int height = g2.getFontMetrics(font).getHeight();
        float fontSizeW = (float) ((rect.getWidth() / width ) * fontSize);
        float fontSizeH = (float) ((rect.getHeight() / height ) * fontSize);
        fontSize = Math.min(fontSizeW, fontSizeH);
        font = g2.getFont().deriveFont(fontSize);
        FontRenderContext context = g2.getFontRenderContext();
        g2.setFont(font);//what is this?
        int textWidth = (int) font.getStringBounds(str, context).getWidth();
        LineMetrics ln = font.getLineMetrics(str, context);
        int textHeight = (int) (ln.getAscent() + ln.getDescent());
        int x1 = (int) (rect.x + (rect.getWidth() - textWidth)/2);
        int y1 = (int)(rect.y + (rect.getHeight() + textHeight)/2 - ln.getDescent());

        g2.drawString(str, x1, y1);
    }

    @Override public void drawOval(AEColor param, AERect drawB) {
        p.getLastGraphics().setColor(param==null?Color.black:new Color(param.getRed(), param.getGreen(), param.getBlue()));
        p.getLastGraphics().drawOval((int)drawB.x, (int)drawB.y, (int)drawB.getWidth(), (int)drawB.getHeight());
    }

    @Override public void drawRect(AEColor param, AERect drawB) {
        p.getLastGraphics().setColor(param==null?Color.black:new Color(param.getRed(), param.getGreen(), param.getBlue()));
        p.getLastGraphics().drawRect((int)drawB.x, (int)drawB.y, (int)drawB.getWidth(), (int)drawB.getHeight());
    }

    @Override public void drawHalfOval(AEColor param, AERect aeRect, int openDirection) {
        p.getLastGraphics().setColor(param==null?Color.black:new Color(param.getRed(), param.getGreen(), param.getBlue()));
        if(openDirection==0) {
            p.getLastGraphics().draw(new Arc2D.Double(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 180, 180, Arc2D.OPEN));
        } else if(openDirection==1) {
            p.getLastGraphics().draw(new Arc2D.Double(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 0, 180, Arc2D.OPEN));
        } else if(openDirection==2) {
            p.getLastGraphics().draw(new Arc2D.Double(aeRect.x, aeRect.y, aeRect.getWidth(), aeRect.getHeight(), 90, 180, Arc2D.OPEN));
        } else if(openDirection==3) {
            p.getLastGraphics().draw(new Arc2D.Double(aeRect.x, aeRect.y, aeRect.getWidth(), aeRect.getHeight(), 90, -180, Arc2D.OPEN));
        }
    }
    @Override public void fillHalfOval(AEColor param, AERect aeRect, int openDirection) {
        p.getLastGraphics().setColor(param==null?Color.black:new Color(param.getRed(), param.getGreen(), param.getBlue()));
        if(openDirection==0) {
            p.getLastGraphics().fill(new Arc2D.Double(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 180, 180, Arc2D.OPEN));
        } else if(openDirection==1) {
            p.getLastGraphics().fill(new Arc2D.Double(aeRect.x, aeRect.y, aeRect.w, aeRect.h, 0, 180, Arc2D.OPEN));
        } else if(openDirection==2) {
            p.getLastGraphics().fill(new Arc2D.Double(aeRect.x, aeRect.y, aeRect.getWidth(), aeRect.getHeight(), 90, 180, Arc2D.OPEN));
        } else if(openDirection==3) {
            p.getLastGraphics().fill(new Arc2D.Double(aeRect.x, aeRect.y, aeRect.getWidth(), aeRect.getHeight(), 90, -180, Arc2D.OPEN));
        }
    }

    @Override public void fillTriangle(AEColor param, AERect aeRect) {
        p.getLastGraphics().setColor(param==null?Color.black:new Color(param.getRed(), param.getGreen(), param.getBlue()));
        int[] xp = { (int)aeRect.x, (int)(aeRect.x + aeRect.w), (int) (aeRect.x + aeRect.w/2)};
        int[] yp = { (int)(aeRect.y + aeRect.h/4), (int)(aeRect.y + aeRect.h/4), (int) (aeRect.y + aeRect.h)};
        p.getLastGraphics().fillPolygon(xp, yp, xp.length);
    }
}