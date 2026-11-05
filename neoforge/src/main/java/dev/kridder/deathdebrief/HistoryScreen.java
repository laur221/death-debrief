package dev.kridder.deathdebrief;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HistoryScreen extends Screen {
	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private final Screen parent;
	private int page;
	private int pages = 1;
	private long confirmUntil;
	private Button clearBtn;

	public HistoryScreen(Screen parent) {
		super(Component.translatable("deathdebrief.history.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int n = DeathHistory.size();
		int perPage = Math.max(1, Math.min(8, (height - 28 - 60) / 22));
		pages = Math.max(1, (n + perPage - 1) / perPage);
		page = Math.max(0, Math.min(page, pages - 1));
		int pw = Math.min(width - 20, 400);
		int x = (width - pw) / 2;
		for (int row = 0; row < perPage; row++) {
			int idx = page * perPage + row;
			DeathRecord r = DeathHistory.get(idx);
			if (r == null) {
				break;
			}
			addRenderableWidget(Button.builder(Component.literal(rowText(r)), b -> {
				if (minecraft != null) {
					minecraft.setScreen(new DebriefScreen(this, idx));
				}
			}).bounds(x, 28 + row * 22, pw, 20).build());
		}
		Button prev = Button.builder(Component.translatable("deathdebrief.history.prev"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(x, height - 52, 70, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);
		Button next = Button.builder(Component.translatable("deathdebrief.history.next"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(x + pw - 70, height - 52, 70, 20).build();
		next.active = page < pages - 1;
		addRenderableWidget(next);
		int half = (pw - 4) / 2;
		clearBtn = Button.builder(Component.translatable("deathdebrief.history.clear"), b -> {
			long now = System.currentTimeMillis();
			if (now < confirmUntil) {
				DeathHistory.clear();
				confirmUntil = 0;
				page = 0;
				rebuildWidgets();
			} else {
				confirmUntil = now + 3000;
				b.setMessage(Component.translatable("deathdebrief.history.clear_confirm"));
			}
		}).bounds(x, height - 28, half, 20).build();
		addRenderableWidget(clearBtn);
		addRenderableWidget(Button.builder(Component.translatable("deathdebrief.history.done"), b -> onClose())
				.bounds(x + half + 4, height - 28, half, 20).build());
		clearBtn.active = n > 0;
	}

	private static String rowText(DeathRecord r) {
		String date = FMT.format(Instant.ofEpochMilli(r.receivedAt()).atZone(ZoneId.systemDefault()));
		List<HitRecord> hits = r.hits();
		String cause;
		if (!r.killer().isEmpty()) {
			cause = r.killer();
		} else if (!hits.isEmpty()) {
			cause = DebriefScreen.sourceName(hits.get(hits.size() - 1));
		} else {
			cause = r.deathMessage().getString();
		}
		return date + "  |  " + cause + "  |  " + DebriefScreen.dimensionName(r.dimension()) + "  |  " + r.x() + " "
				+ r.y() + " " + r.z();
	}

	@Override
	public void tick() {
		if (confirmUntil != 0 && System.currentTimeMillis() >= confirmUntil && clearBtn != null) {
			confirmUntil = 0;
			clearBtn.setMessage(Component.translatable("deathdebrief.history.clear"));
		}
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
		if (DeathHistory.size() == 0) {
			ctx.drawCenteredString(font, Component.translatable("deathdebrief.no_report"), width / 2,
					height / 2 - 4, 0xAAAAAA);
		}
		ctx.drawCenteredString(font, Component.translatable("deathdebrief.history.page", page + 1, pages),
				width / 2, height - 46, 0xAAAAAA);
	}

	Screen getParent() {
		return parent;
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}
}
