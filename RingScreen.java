package com.example.greenlantern.client;

import com.example.greenlantern.ModNetwork;
import com.example.greenlantern.RingPowers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Ventana del anillo: rejilla de objetos con buscador, parecida al inventario creativo. */
public class RingScreen extends Screen {
    private static final int COLS = 9, ROWS = 6, CELL = 20;
    private static List<ItemStack> ALL;

    private final List<ItemStack> shown = new ArrayList<>();
    private final int panelW = COLS * CELL + 16;
    private final int panelH = ROWS * CELL + 62;
    private EditBox search;
    private int scroll = 0;
    private int left, top;

    public RingScreen() {
        super(Component.literal("Anillo de Poder"));
    }

    private static void buildAll() {
        if (ALL != null) return;
        List<ItemStack> list = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (RingPowers.isAllowed(item)) list.add(new ItemStack(item));
        }
        list.sort(Comparator.comparing(s -> ForgeRegistries.ITEMS.getKey(s.getItem()).toString()));
        ALL = list;
    }

    @Override
    protected void init() {
        buildAll();
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;
        String prev = search != null ? search.getValue() : "";
        search = new EditBox(this.font, left + 8, top + 20, panelW - 16, 14, Component.literal("Buscar"));
        search.setMaxLength(40);
        search.setValue(prev);
        search.setResponder(t -> applyFilter());
        addRenderableWidget(search);
        setInitialFocus(search);
        applyFilter();
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (ItemStack s : ALL) {
            if (q.isEmpty()
                    || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || ForgeRegistries.ITEMS.getKey(s.getItem()).getPath().contains(q)) {
                shown.add(s);
            }
        }
        scroll = 0;
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + COLS - 1) / COLS - ROWS);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, 0xFF2E6B45);
        g.fill(left, top, left + panelW, top + panelH, 0xFF0E1F15);
        g.drawString(this.font, this.title, left + 8, top + 6, 0x55FF77, false);

        ItemStack hovered = ItemStack.EMPTY;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c;
                int x = left + 8 + c * CELL;
                int y = top + 40 + r * CELL;
                boolean over = mx >= x && mx < x + CELL && my >= y && my < y + CELL;
                g.fill(x, y, x + CELL - 1, y + CELL - 1, over ? 0xFF2F6B45 : 0xFF163325);
                if (idx < shown.size()) {
                    ItemStack st = shown.get(idx);
                    g.renderItem(st, x + 1, y + 1);
                    g.fill(x + 1, y + 1, x + 17, y + 17, 0x6600FF55); // tinte verde translucido
                    if (over) hovered = st;
                }
            }
        }
        g.drawString(this.font, "Clic: 1  |  Mayus + clic: pila  |  Rueda: desplazar",
                left + 8, top + panelH - 14, 0x88CC99, false);

        super.render(g, mx, my, pt);
        if (!hovered.isEmpty()) g.renderTooltip(this.font, hovered, mx, my);
    }

    private ItemStack itemAt(double mx, double my) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = left + 8 + c * CELL;
                int y = top + 40 + r * CELL;
                if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) {
                    int idx = (scroll + r) * COLS + c;
                    return idx < shown.size() ? shown.get(idx) : ItemStack.EMPTY;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        ItemStack st = itemAt(mx, my);
        if (!st.isEmpty() && button == 0) {
            int amount = hasShiftDown() ? st.getMaxStackSize() : 1;
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ConjurePacket(ForgeRegistries.ITEMS.getKey(st.getItem()), amount));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
