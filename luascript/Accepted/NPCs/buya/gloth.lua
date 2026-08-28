GlothNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.quest["gloth_override"] == 1 then
			player:dialogSeq({t, "Sehat selalu, yang terhormat."}, 0)
			return
		end

		if player.quest["gloth_clicked"] == 0 then
			player:dialogSeq(
				{
					t,
					"Siapa di sana? Untuk apa kau datang ke sini?",
					"Kau tidak boleh melewatiku tanpa izin, sebab akulah penjaga rahasia itu.",
					"Hanya mereka yang bersinar oleh kebajikan boleh lewat jalan ini.",
					"Kau menunjukkan secercah harapan, anak muda. Meski waktumu di dunia ini hanya sepersekian waktuku, kau menunjukkan potensi.",
					"Carilah jawabannya dari mereka yang menempatkanku di sini menjaga jalan ini, kedua yang agung itu."
				},
				1
			)
			player.quest["gloth_clicked"] = 1
			return
		end

		if player.quest["gloth_override"] == 0 then
			local weap = player:getEquippedItem(EQ_WEAP)

			if weap.yname == "star_sword" then
				player.quest["gloth_override"] = 1
				player:dialogSeq(
					{t, "Pedangnya kau bawa! Sekarang kau boleh lewat."},
					0
				)
				return
			else
				player:warp(player.m, player.x, player.y - 2)
				player:dialogSeq(
					{t, "Tanpa menunjukkan pedangnya kepadaku, kau tidak akan kuizinkan lewat."},
					0
				)
			end
			return
		end
	end),

	denyClick = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.quest["gloth_override"] == 0 then
			if player.x >= 14 and player.x <= 16 and player.y >= 13 then
				local weap = player:getEquippedItem(EQ_WEAP)

				if weap.yname ~= "star_sword" then
					player:warp(player.m, player.x, player.y - 2)
					return
				end

				if weap.yname == "star_sword" then
					player.quest["gloth_override"] = 1
					player:dialogSeq(
						{t, "Pedangnya kau bawa! Sekarang kau boleh lewat."},
						0
					)
					return
				end

				return
			end
		end
	end
}
