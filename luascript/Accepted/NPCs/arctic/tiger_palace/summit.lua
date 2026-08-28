SummitNpc = {
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)

		if speech == "sucikan kutukan" or speech == "sucikan" then
			SummitNpc.changeAlignment(player, npc)
		end
	end),

	changeAlignment = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.level < 99 or player.baseHealth < 20000 or player.baseMagic < 10000 or player.alignment == 0 then
			-- below minimum stats or no alignment
			player:dialogSeq({t, "Aku tidak bisa menolongmu."}, 0)
			return
		end

		player:dialogSeq(
			{
				t,
				"Hahaha.. jadi yang agung kembali kepadaku, ya, Tuan?",
				"Terjerat masalah, lalu berlari kembali kepadaku?",
				"Ya, dari tingkat \"kehidupan\" ini aku bisa melihat ke dalam jiwamu, dan kulihat jiwamu tidak murni.",
				"Kuanggap kau ingin kubantu lagi, setelah apa yang kau lakukan padaku?"
			},
			1
		)

		local choice = player:menuSeq(
			"Nah, kau sungguh ingin kubantu? Kau tahu itu akan sangat mahal bagimu.",
			{"Ya, aku butuh bantuan.", "Tidak, aku tidak butuh bantuan."},
			{}
		)

		if choice == 1 then
			player.baseHealth = player.baseHealth - 10000
			player.baseMagic = player.baseMagic - 5000
			player.registry["baseHealth"] = player.baseHealth
			player.registry["baseMagic"] = player.baseMagic
			player.health = player.baseHealth
			player.magic = player.baseMagic
			player:calcStat()
			player:sendStatus()
			player:swapAlignment(0)

			player:dialogSeq({t, "Kini kau tidak berpihak ke mana pun."}, 0)
		elseif choice == 2 then
			player:dialogSeq({t, "Temui aku lagi kalau kau berubah pikiran."}, 0)
			return
		end
	end,

	move = function(npc, owner)
		local found
		local moved = true
		local oldside = npc.side
		local checkmove = math.random(0, 10)

		if (npc.retDist <= distanceXY(npc, npc.startX, npc.startY) and npc.retDist > 1 and npc.returning == false) then
			npc.returning = true
		elseif (npc.returning == true and npc.retDist > distanceXY(npc, npc.startX, npc.startY) and npc.retDist > 1) then
			npc.returning = false
		end

		if (npc.returning == true) then
			found = toStart(npc, npc.startX, npc.startY)
		else
			if (checkmove >= 4) then
				npc.side = math.random(0, 3)
				npc:sendSide()
				if (npc.side == oldside) then
					moved = npc:move()
				end
			end
		end

		if (found == true) then
			npc.returning = false
		end
	end
}
