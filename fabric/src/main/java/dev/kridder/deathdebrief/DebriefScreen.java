package dev.kridder.deathdebrief;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

public class DebriefScreen extends Screen {
	private static final long DESPAWN_MS = 300_000L;
	private static final int BG = 0xE0101010;
	private static final int BORDER = 0xFFAA2222;
	private static final int CARD = 0x60303030;
	private static final int ROW_H = 11;
	private static final int OLD_COLOR = 0xFF888888;
	private static final double OLD_SECS = 30.0;
	private static final String SKULL = "☠";
	private static final String HEART = "♥";

	private static final Identifier HEART_FULL = Identifier.ofVanilla("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.ofVanilla("hud/heart/half");
	private static final Identifier HEART_CONTAINER = Identifier.ofVanilla("hud/heart/container");

	private record Rect(int x, int y, int w, int h) {
		int right() {
			return x + w;
		}

		int bottom() {
			return y + h;
		}
	}

	private final Screen parent;
	private final int index;
	private boolean compact;
	private int left;
	private int top;
	private int pw;
	private int ph;
	private Rect figR;
	private Rect causeR;
	private Rect badgesR;
	private Rect timeR;
	private Rect tableR;
	private Rect armorR; // compact only
	private int rowsShown;
	private List<OrderedText> causeLines = List.of();

	public DebriefScreen(Screen parent) {
		this(parent, 0);
	}

	public DebriefScreen(Screen parent, int index) {
		super(Text.translatable("deathdebrief.title"));
		this.parent = parent;
		this.index = index;
	}

	/** The screen this debrief flow was originally opened from (skipping history/debrief hops). */
	private Screen origin() {
		Screen s = parent;
		int guard = 0;
		while (guard++ < 8) {
			if (s instanceof HistoryScreen h) {
				s = h.getParent();
			} else if (s instanceof DebriefScreen d) {
				s = d.parent;
			} else {
				break;
			}
		}
		return s;
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		DeathRecord rep = DeathHistory.get(index);
		compact = width < 460 || height < 270;
		int pad = 6;
		if (!compact) {
			pw = 440;
			ph = 250;
		} else {
			pw = Math.min(width - 8, 330);
			ph = height - 8;
		}
		left = (width - pw) / 2;
		top = (height - ph) / 2;
		int btnY = top + ph - pad - 20;
		int contentTop = top + 20;
		int contentBottom = btnY - 4;
		int tlH = 48;

		if (!compact) {
			int colX = left + pad;
			figR = new Rect(colX, contentTop, 110, 88);
			causeR = new Rect(colX, figR.bottom() + 4, 110, contentBottom - figR.bottom() - 4);
			int rx = left + pad + 110 + pad;
			int rw = pw - (rx - left) - pad;
			badgesR = new Rect(rx, contentTop, rw, 40);
			timeR = new Rect(rx, badgesR.bottom() + 4, rw, tlH);
			tableR = new Rect(rx, timeR.bottom() + 4, rw, contentBottom - timeR.bottom() - 4);
		} else {
			int cx = left + pad;
			int cw = pw - pad * 2;
			causeR = new Rect(cx, contentTop, cw, 46);
			armorR = new Rect(causeR.right() - 4 * 18 - 3 * 2 - 4, causeR.y + 4, 4 * 18 + 3 * 2, 18);
			badgesR = new Rect(cx, causeR.bottom() + 3, cw, 42);
			timeR = new Rect(cx, badgesR.bottom() + 3, cw, tlH);
			tableR = new Rect(cx, timeR.bottom() + 3, cw, Math.max(0, contentBottom - timeR.bottom() - 3));
		}
		rowsShown = 0;
		if (rep != null) {
			int avail = tableR.h - 4 - 11;
			rowsShown = Math.max(0, Math.min(rep.hits().size(), avail / ROW_H));
			int textW = compact ? causeR.w - 30 - (4 * 18 + 3 * 2 + 8) : causeR.w - 8;
			Text msg = causeText(rep);
			List<OrderedText> lines = textRenderer.wrapLines(msg, Math.max(40, textW));
			int maxLines = compact ? 3 : Math.max(1, (causeR.h - 56) / 11);
			causeLines = new ArrayList<>(lines.subList(0, Math.min(lines.size(), maxLines)));
		}

		// history navigation in the title bar
		int size = DeathHistory.size();
		ButtonWidget older = ButtonWidget.builder(Text.literal("<"), b -> goTo(index + 1))
				.dimensions(left + 6, top + 3, 14, 12).build();
		older.active = index + 1 < size;
		addDrawableChild(older);
		ButtonWidget newer = ButtonWidget.builder(Text.literal(">"), b -> goTo(index - 1))
				.dimensions(left + 22, top + 3, 14, 12).build();
		newer.active = index > 0;
		addDrawableChild(newer);

		// bottom buttons
		boolean respawn = origin() instanceof DeathScreen && index == 0;
		int n = respawn ? 5 : 4;
		int gap = 3;
		int bw = (pw - pad * 2 - (n - 1) * gap) / n;
		int bx = left + pad;
		addDrawableChild(ButtonWidget.builder(Text.translatable("deathdebrief.waypoint"), b -> waypoint())
				.dimensions(bx, btnY, bw, 20).build());
		bx += bw + gap;
		addDrawableChild(ButtonWidget.builder(Text.translatable("deathdebrief.copy"), b -> {
			DeathRecord r = DeathHistory.get(index);
			if (r != null && client != null) {
				client.keyboard.setClipboard(r.x() + " " + r.y() + " " + r.z());
			}
		}).dimensions(bx, btnY, bw, 20).build());
		bx += bw + gap;
		if (respawn) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("deathdebrief.respawn"), b -> {
				if (client != null && client.player != null) {
					client.player.requestRespawn();
					client.setScreen(null);
				}
			}).dimensions(bx, btnY, bw, 20).build());
			bx += bw + gap;
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("deathdebrief.history"), b -> {
			if (client == null) {
				return;
			}
			if (parent instanceof HistoryScreen) {
				client.setScreen(parent);
			} else {
				client.setScreen(new HistoryScreen(this));
			}
		}).dimensions(bx, btnY, bw, 20).build());
		bx += bw + gap;
		addDrawableChild(ButtonWidget.builder(Text.translatable("deathdebrief.close"), b -> close())
				.dimensions(bx, btnY, bw, 20).build());
	}

	private void goTo(int i) {
		if (client != null && DeathHistory.get(i) != null) {
			client.setScreen(new DebriefScreen(parent, i));
		}
	}

	/** Sends a client-side chat line whose click copies the death coordinates. */
	private void waypoint() {
		DeathRecord r = DeathHistory.get(index);
		if (r == null || client == null) {
			return;
		}
		String coords = r.x() + " " + r.y() + " " + r.z();
		MutableText msg = Text.translatable("deathdebrief.waypoint_msg", coords, dimensionName(r.dimension()))
				.styled(s -> s.withColor(Formatting.AQUA).withUnderline(true)
						.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, coords))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Text.translatable("deathdebrief.waypoint_hint"))));
		client.inGameHud.getChatHud().addMessage(msg);
		client.keyboard.setClipboard(coords);
		WaypointManager.place(r, true);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// no blur: darkening is drawn in render()
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ----------------------------------------------------------------- helpers

	private static Text causeText(DeathRecord rep) {
		Text full = rep.deathMessage();
		String msg = full.getString();
		String k = rep.killer();
		int idx = k.isEmpty() ? -1 : msg.indexOf(k);
		if (idx < 0) {
			return full;
		}
		return Text.literal(msg.substring(0, idx)).formatted(Formatting.WHITE)
				.append(Text.literal(k).formatted(Formatting.GREEN))
				.append(Text.literal(msg.substring(idx + k.length())).formatted(Formatting.WHITE));
	}

	static String sourceName(HitRecord h) {
		if (!h.attacker().isEmpty()) {
			return h.attacker();
		}
		String key = "deathdebrief.source." + h.source();
		return Language.getInstance().hasTranslation(key) ? Language.getInstance().get(key) : h.source();
	}

	private static String hearts(float dmg) {
		return String.format(Locale.ROOT, "%.1f %s", dmg / 2.0f, HEART);
	}

	/** Relative time before death: "-12.3s" under a minute, "-2m15s" otherwise. */
	private static String fmtTime(double secs) {
		double a = Math.abs(secs);
		String sign = a < 0.05 ? "" : "-";
		if (a < 60.0) {
			return String.format(Locale.ROOT, "%s%.1fs", sign, a);
		}
		int total = (int) a;
		return String.format(Locale.ROOT, "%s%dm%02ds", sign, total / 60, total % 60);
	}

	private static Item iconItem(HitRecord h) {
		String s = h.source().toLowerCase(Locale.ROOT).replace("_", "");
		if (s.contains("lava")) {
			return Items.LAVA_BUCKET;
		}
		if (s.contains("onfire")) {
			return Items.BLAZE_POWDER;
		}
		if (s.contains("infire") || s.contains("fire")) {
			return Items.FLINT_AND_STEEL;
		}
		if (s.startsWith("fall") && !s.contains("fallingblock")) {
			return Items.FEATHER;
		}
		if (s.contains("drown")) {
			return Items.WATER_BUCKET;
		}
		if (s.contains("explosion")) {
			return Items.TNT;
		}
		if (s.contains("arrow")) {
			return Items.ARROW;
		}
		if (s.contains("starve")) {
			return Items.ROTTEN_FLESH;
		}
		if (s.contains("magic") || s.contains("wither")) {
			return Items.POTION;
		}
		String t = h.attackerType();
		if (!t.isEmpty()) {
			Optional<EntityType<?>> et = EntityType.get(t);
			if (et.isPresent()) {
				SpawnEggItem egg = SpawnEggItem.forEntity(et.get());
				if (egg != null) {
					return egg;
				}
			}
			return Items.IRON_SWORD;
		}
		if (s.contains("mob") || s.contains("player")) {
			return Items.IRON_SWORD;
		}
		return Items.SKELETON_SKULL;
	}

	private static ItemStack icon(HitRecord h) {
		return new ItemStack(iconItem(h));
	}

	static String dimensionName(String dim) {
		return switch (dim) {
		case "minecraft:overworld" -> Text.translatable("deathdebrief.dim.overworld").getString();
		case "minecraft:the_nether" -> Text.translatable("deathdebrief.dim.nether").getString();
		case "minecraft:the_end" -> Text.translatable("deathdebrief.dim.end").getString();
		default -> dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
		};
	}

	static String shortDimName(String dim) {
		return switch (dim) {
		case "minecraft:overworld" -> "Overworld";
		case "minecraft:the_nether" -> "Nether";
		case "minecraft:the_end" -> "End";
		default -> dimensionName(dim);
		};
	}

	private static Item dimensionIcon(String dim) {
		return switch (dim) {
		case "minecraft:the_nether" -> Items.NETHERRACK;
		case "minecraft:the_end" -> Items.END_STONE;
		default -> Items.GRASS_BLOCK;
		};
	}

	private int fitW(String s, float scale) {
		return (int) (textRenderer.getWidth(s) * scale);
	}

	/** Draws text scaled; shrinks (never below 0.5) so it fits maxW. */
	private void text(DrawContext c, String s, int x, int y, int color, float scale, int maxW) {
		float sc = scale;
		int w = textRenderer.getWidth(s);
		if (maxW > 0 && w * sc > maxW) {
			sc = Math.max(0.75f, (float) maxW / w);
			if (w * sc > maxW) {
				s = textRenderer.trimToWidth(s, (int) (maxW / sc));
			}
		}
		var m = c.getMatrices();
		m.push();
		m.translate(x, y, 0);
		m.scale(sc, sc, 1.0f);
		c.drawTextWithShadow(textRenderer, s, 0, 0, color);
		m.pop();
	}

	private void textCentered(DrawContext c, String s, int cx, int y, int color, float scale) {
		text(c, s, cx - fitW(s, scale) / 2, y, color, scale, 0);
	}

	private void item(DrawContext c, ItemStack st, int x, int y, float scale) {
		var m = c.getMatrices();
		m.push();
		m.translate(x, y, 0);
		m.scale(scale, scale, 1.0f);
		c.drawItem(st, 0, 0);
		m.pop();
	}

	private void card(DrawContext c, Rect r) {
		c.fill(r.x, r.y, r.right(), r.bottom(), CARD);
	}

	private void slot(DrawContext c, int x, int y, ItemStack st) {
		c.fill(x, y, x + 18, y + 18, 0xA0101010);
		c.drawBorder(x, y, 18, 18, 0xFF555555);
		if (!st.isEmpty()) {
			c.drawItem(st, x + 1, y + 1);
		}
	}

	// ------------------------------------------------------------------ render

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0xA0000000);
		ctx.fill(left, top, left + pw, top + ph, BG);
		ctx.drawBorder(left, top, pw, ph, BORDER);
		Text head = Text.literal(SKULL + " ").formatted(Formatting.RED)
				.append(title.copy().formatted(Formatting.WHITE, Formatting.BOLD));
		ctx.drawCenteredTextWithShadow(textRenderer, head, width / 2, top + 7, 0xFFFFFF);
		ctx.fill(left + 6, top + 17, left + pw - 6, top + 18, BORDER);

		DeathRecord r = DeathHistory.get(index);
		if (r == null) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("deathdebrief.no_report"), width / 2,
					top + ph / 2 - 4, 0xAAAAAA);
			super.render(ctx, mouseX, mouseY, delta);
			return;
		}
		int size = DeathHistory.size();
		ctx.drawTextWithShadow(textRenderer, Text.translatable("deathdebrief.history.position", size - index, size),
				left + 40, top + 5, 0xAAAAAA);
		List<HitRecord> hits = r.hits();
		HitRecord fatal = hits.isEmpty() ? null : hits.get(hits.size() - 1);
		ItemStack tooltip = ItemStack.EMPTY;

		// ---- cause card (and figure in full mode)
		if (!compact) {
			card(ctx, figR);
			int sy = figR.y + 5;
			for (int i = 0; i < 4; i++) {
				ItemStack st = i < r.armor().size() ? r.armor().get(i) : ItemStack.EMPTY;
				int sx = figR.x + 4;
				int yy = sy + i * 20;
				slot(ctx, sx, yy, st);
				if (!st.isEmpty() && mouseX >= sx && mouseX < sx + 18 && mouseY >= yy && mouseY < yy + 18) {
					tooltip = st;
				}
			}
			if (client != null && client.player != null) {
				var p = client.player;
				int oldDeath = p.deathTime;
				p.deathTime = 0;
				try {
					InventoryScreen.drawEntity(ctx, figR.x + 26, figR.y + 2, figR.right() - 2, figR.bottom() - 2, 36,
							0.0625f, mouseX, mouseY, p);
				} catch (Throwable t) {
					// ignore render failures of the figure
				} finally {
					p.deathTime = oldDeath;
				}
			}
			card(ctx, causeR);
			text(ctx, Text.translatable("deathdebrief.cause").getString(), causeR.x + 4, causeR.y + 4, 0xFF7777, 1.0f,
					causeR.w - 8);
			item(ctx, fatal != null ? icon(fatal) : new ItemStack(Items.SKELETON_SKULL),
					causeR.x + (causeR.w - 32) / 2, causeR.y + 16, 2.0f);
			int ly = causeR.y + 54;
			for (OrderedText line : causeLines) {
				ctx.drawTextWithShadow(textRenderer, line, causeR.x + 4, ly, 0xFFFFFF);
				ly += 11;
			}
		} else {
			card(ctx, causeR);
			text(ctx, Text.translatable("deathdebrief.cause").getString(), causeR.x + 4, causeR.y + 3, 0xFF7777, 0.85f,
					100);
			item(ctx, fatal != null ? icon(fatal) : new ItemStack(Items.SKELETON_SKULL), causeR.x + 4, causeR.y + 16, 1.5f);
			int ly = causeR.y + 15;
			for (OrderedText line : causeLines) {
				ctx.drawTextWithShadow(textRenderer, line, causeR.x + 32, ly, 0xFFFFFF);
				ly += 10;
			}
			for (int i = 0; i < 4; i++) {
				ItemStack st = i < r.armor().size() ? r.armor().get(i) : ItemStack.EMPTY;
				int sx = armorR.x + i * 20;
				slot(ctx, sx, armorR.y, st);
				if (!st.isEmpty() && mouseX >= sx && mouseX < sx + 18 && mouseY >= armorR.y && mouseY < armorR.y + 18) {
					tooltip = st;
				}
			}
		}

		// ---- badges
		long remaining = DESPAWN_MS - (System.currentTimeMillis() - r.receivedAt());
		boolean ring = !r.keepInventory() && r.stacks() > 0;
		boolean live = ring && r == DeathHistory.sessionRecord && remaining > 0;
		String coords = r.x() + " " + r.y() + " " + r.z();
		String stacksVal;
		if (r.keepInventory()) {
			stacksVal = Text.translatable("deathdebrief.kept").getString();
		} else if (r.stacks() == 0) {
			stacksVal = Text.translatable("deathdebrief.no_items").getString();
		} else if (r.stacks() == 1) {
			stacksVal = Text.translatable("deathdebrief.stack_one").getString();
		} else {
			stacksVal = Text.translatable("deathdebrief.stacks", r.stacks()).getString();
		}
		String timeVal = live ? String.format(Locale.ROOT, "%d:%02d", remaining / 60000, (remaining / 1000) % 60)
				: Text.translatable("deathdebrief.expired").getString();
		String[] labels = {
				Text.translatable("deathdebrief.badge.coords").getString(),
				Text.translatable("deathdebrief.badge.dimension").getString(),
				Text.translatable("deathdebrief.badge.xp").getString(),
				Text.translatable("deathdebrief.badge.items").getString() };
		String[] values = { coords, dimensionName(r.dimension()), String.valueOf(r.xpLevel()), stacksVal };
		ItemStack[] icons = { new ItemStack(Items.COMPASS), new ItemStack(dimensionIcon(r.dimension())),
				new ItemStack(Items.EXPERIENCE_BOTTLE), new ItemStack(Items.CHEST) };
		int[] colors = { 0xFFFFFF, 0xFFFFFF, 0x55FF55,
				r.keepInventory() ? 0x55FFFF : (r.stacks() == 0 ? 0xAAAAAA : 0xFFFFFF) };
		if (!compact) {
			int ringW = ring ? 54 : 0;
			int avail = badgesR.w - ringW - (ring ? 3 : 0) - 9;
			int[] want = new int[4];
			int sum = 0;
			for (int pass = 0; pass < 2; pass++) {
				if (pass == 1) {
					values[0] = "X " + r.x() + " Y " + r.y() + "\nZ " + r.z();
					values[1] = shortDimName(r.dimension());
				}
				sum = 0;
				for (int i = 0; i < 4; i++) {
					int vw = 0;
					for (String part : values[i].split("\n")) {
						vw = Math.max(vw, fitW(part, 0.75f));
					}
					int lw = values[i].indexOf('\n') >= 0 ? 0 : fitW(labels[i], 0.75f);
					want[i] = 24 + Math.max(lw, vw) + 4;
					sum += want[i];
				}
				if (sum <= avail) {
					break;
				}
			}
			int bx = badgesR.x;
			for (int i = 0; i < 4; i++) {
				int w = sum > avail ? want[i] * avail / sum : want[i];
				badge(ctx, new Rect(bx, badgesR.y, w, badgesR.h), icons[i], labels[i], values[i], colors[i]);
				bx += w + 3;
			}
			if (ring) {
				Rect rr = new Rect(badgesR.right() - ringW, badgesR.y, ringW, badgesR.h);
				if (live) {
					drawRing(ctx, rr, remaining, timeVal);
				} else {
					drawExpired(ctx, rr, timeVal);
				}
			}
		} else {
			int cw = (badgesR.w - 6) / 3;
			int ch = (badgesR.h - 3) / 2;
			for (int i = 0; i < 4; i++) {
				badge(ctx, new Rect(badgesR.x + (i % 3) * (cw + 3), badgesR.y + (i / 3) * (ch + 3), cw, ch), icons[i],
						labels[i], values[i], colors[i]);
			}
			if (ring) {
				badge(ctx, new Rect(badgesR.x + 1 * (cw + 3), badgesR.y + (ch + 3), cw, ch), new ItemStack(Items.CLOCK),
						Text.translatable("deathdebrief.despawn").getString(), timeVal,
						!live ? 0x888888 : (remaining < 60000 ? 0xFFAA00 : 0xFFFF55));
			}
		}

		timeline(ctx, timeR, r);
		table(ctx, tableR, r);

		super.render(ctx, mouseX, mouseY, delta);
		if (!tooltip.isEmpty()) {
			ctx.drawItemTooltip(textRenderer, tooltip, mouseX, mouseY);
		}
	}

	private void badge(DrawContext c, Rect r, ItemStack icon, String label, String value, int vColor) {
		card(c, r);
		c.drawItem(icon, r.x + 3, r.y + (r.h - 16) / 2);
		int tx = r.x + 22;
		int maxW = r.w - 24;
		int mid = r.y + r.h / 2;
		if (value.indexOf('\n') >= 0) {
			String[] ln = value.split("\n");
			text(c, ln[0], tx, mid - 8, vColor, 0.75f, maxW);
			text(c, ln[1], tx, mid + 1, vColor, 0.75f, maxW);
			return;
		}
		float vs = textRenderer.getWidth(value) <= maxW ? 1.0f : 0.75f;
		if (fitW(label, 0.75f) <= maxW) {
			text(c, label, tx, mid - 9, 0xAAAAAA, 0.75f, maxW);
			text(c, value, tx, mid, vColor, vs, maxW);
		} else {
			// label does not fit at a readable size: icon + value only
			text(c, value, tx, mid - (int) (4 * vs), vColor, vs, maxW);
		}
	}

	private void drawExpired(DrawContext c, Rect r, String value) {
		textCentered(c, Text.translatable("deathdebrief.despawn").getString(), r.x + r.w / 2, r.y + 1, 0xAAAAAA, 0.75f);
		int cy = r.y + 10 + 14;
		textCentered(c, value, r.x + r.w / 2, cy - 3, 0x888888, 0.85f);
	}

	private void drawRing(DrawContext c, Rect r, long remaining, String timeVal) {
		textCentered(c, Text.translatable("deathdebrief.despawn").getString(), r.x + r.w / 2, r.y + 1, 0xAAAAAA, 0.75f);
		int dia = 28;
		int cx = r.x + r.w / 2;
		int cy = r.y + 10 + dia / 2;
		double rOut = dia / 2.0;
		double rIn = rOut - 3.0;
		double frac = Math.max(0, Math.min(1.0, remaining / (double) DESPAWN_MS));
		for (int dy = -(int) rOut - 1; dy <= (int) rOut; dy++) {
			for (int dx = -(int) rOut - 1; dx <= (int) rOut; dx++) {
				double px = dx + 0.5;
				double py = dy + 0.5;
				double d2 = px * px + py * py;
				if (d2 > rOut * rOut || d2 < rIn * rIn) {
					continue;
				}
				double ang = Math.atan2(px, -py);
				if (ang < 0) {
					ang += Math.PI * 2;
				}
				boolean on = ang / (Math.PI * 2) <= frac;
				c.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, on ? 0xFFDD2222 : 0xFF3A3A3A);
			}
		}
		textCentered(c, timeVal, cx, cy - 3, remaining <= 0 ? 0xFF5555 : 0xFFFFFF, 0.75f);
	}

	private void timeline(DrawContext c, Rect r, DeathRecord rep) {
		card(c, r);
		c.drawTextWithShadow(textRenderer, Text.translatable("deathdebrief.timeline"), r.x + 4, r.y + 3, 0xFFFFFF);
		int ax0 = r.x + 14;
		int ax1 = r.right() - 14;
		int ay = r.y + r.h - 16;
		c.fill(ax0, ay, ax1 + 1, ay + 1, 0xFF888888);
		for (int i = 0; i <= 5; i++) {
			int sec = -10 + 2 * i;
			int x = ax0 + i * (ax1 - ax0) / 5;
			c.fill(x, ay - 2, x + 1, ay + 3, 0xFF888888);
			textCentered(c, sec + "s", x, ay + 5, 0xAAAAAA, 0.75f);
		}
		List<HitRecord> hits = rep.hits();
		for (int i = 0; i < hits.size(); i++) {
			HitRecord h = hits.get(i);
			boolean isFatal = i == hits.size() - 1;
			double secs = (h.tick() - rep.worldTime()) / 20.0;
			boolean old = secs < -OLD_SECS;
			if (secs < -10) {
				secs = -10;
			}
			if (secs > 0) {
				secs = 0;
			}
			int x = ax0 + (int) Math.round((secs + 10.0) / 10.0 * (ax1 - ax0));
			ItemStack st = isFatal ? new ItemStack(Items.SKELETON_SKULL) : icon(h);
			c.drawItem(st, x - 8, ay - 19);
			if (old) {
				c.fill(x - 8, ay - 19, x + 8, ay - 3, 0xA0555555);
			}
		}
	}

	private void table(DrawContext c, Rect r, DeathRecord rep) {
		card(c, r);
		List<HitRecord> hits = rep.hits();
		boolean wide = r.w >= 290;
		int timeX = r.x + 4;
		int timeW = textRenderer.getWidth(Text.translatable("deathdebrief.col.time").getString());
		for (int i = 0; i < rowsShown; i++) {
			HitRecord h = hits.get(hits.size() - 1 - i);
			timeW = Math.max(timeW, textRenderer.getWidth(fmtTime((h.tick() - rep.worldTime()) / 20.0)));
		}
		int srcX = timeX + timeW + 6;
		int dmgX = srcX + (wide ? 88 : 70);
		int hpX = dmgX + (wide ? 92 : 78);
		int hy = r.y + 3;
		int gray = 0xAAAAAA;
		c.drawTextWithShadow(textRenderer, Text.translatable("deathdebrief.col.time"), timeX, hy, gray);
		c.drawTextWithShadow(textRenderer, Text.translatable("deathdebrief.col.source"), srcX, hy, gray);
		c.drawTextWithShadow(textRenderer, Text.translatable("deathdebrief.col.damage"), dmgX, hy, gray);
		c.drawTextWithShadow(textRenderer, Text.translatable("deathdebrief.col.health"), hpX, hy, gray);
		c.fill(r.x + 2, hy + 10, r.right() - 2, hy + 11, 0x60888888);
		for (int i = 0; i < rowsShown; i++) {
			HitRecord h = hits.get(hits.size() - 1 - i);
			boolean isFatal = i == 0;
			int y = r.y + 15 + i * ROW_H;
			double secs = (h.tick() - rep.worldTime()) / 20.0;
			boolean old = !isFatal && secs < -OLD_SECS;
			int color = isFatal ? 0xFF5555 : (old ? OLD_COLOR : 0xFFFFFF);
			if (isFatal) {
				c.fill(r.x + 1, y - 1, r.right() - 1, y + ROW_H - 1, 0x40AA2222);
			}
			c.drawTextWithShadow(textRenderer, fmtTime(secs), timeX, y, isFatal ? 0xFF5555 : (old ? OLD_COLOR : 0xAAAAAA));
			item(c, icon(h), srcX, y, 0.5f);
			c.drawTextWithShadow(textRenderer,
					textRenderer.trimToWidth(sourceName(h) + (h.blocked() ? " [B]" : ""), dmgX - srcX - 12), srcX + 11, y,
					color);
			String dmg = hearts(h.taken());
			c.drawTextWithShadow(textRenderer, dmg, dmgX, y, color);
			String before = "(" + hearts(h.beforeArmor()) + ")";
			int bx = dmgX + textRenderer.getWidth(dmg) + 3;
			if (bx + textRenderer.getWidth(before) <= hpX - 2) {
				c.drawTextWithShadow(textRenderer, before, bx, y, 0x888888);
			}
			// health after: 10 hearts
			int halves = (int) Math.ceil(Math.max(0, h.healthAfter()));
			for (int k = 0; k < 10; k++) {
				int hx = hpX + k * 7;
				c.drawGuiTexture(HEART_CONTAINER, hx, y, 7, 7);
				if (halves >= (k + 1) * 2) {
					c.drawGuiTexture(HEART_FULL, hx, y, 7, 7);
				} else if (halves == k * 2 + 1) {
					c.drawGuiTexture(HEART_HALF, hx, y, 7, 7);
				}
			}
		}
	}
}
