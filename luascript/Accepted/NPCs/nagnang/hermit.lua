local _leviathan = "leviathan"

local _showMenu = function(player)
	local opts = {"tainted_blade", "tainted_staff", "tainted_ring"}

	player:buyExtend(
		"What would you like to buy?",
		opts
	)
end

-- @TODO: Add option to repair cursed equipment

HermitNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		if (player.quest[_leviathan] < 3) then
			player:dialogSeq({"Siapa yang membiarkanmu masuk? Pergi! Aku tidak suka orang asing."}, 1)
			player:warp(2539, 22, 11)
			return
		end

		_showMenu(player)
	end),

	onSayClick = async(function(player, npc)
		Tools.configureDialog(player, npc)

		local speech = string.lower(player.speech)

		if (speech == "dae-whan" and player.quest[_leviathan] < 3) then
			player:dialogSeq(
				{
					"Eh? Jadi kau kawan makhluk hijau besar di selatan itu? Mereka kaum yang baik dan damai. Biarkan aku sendiri, dan aku pun membiarkan mereka.",
					"Kaumku sendiri meninggalkanku. Aku menemukan cara mengutuk senjata dengan ilmu hitam, merusaknya sebagai penukar kekuatan yang dahsyat. Kawan-kawanku takut pada karyaku lalu mengusirku.",
					"Mereka menyebar ciptaanku di antara monster yang sama mematikannya dengan pusaka gelap yang kini mereka jaga. Aku tidak punya kekuatan untuk mengambilnya kembali. Yang bisa kuselamatkan hanya rancanganku yang paling lemah, tercemar dan rendah mutunya.",
					"Meski begitu, yang itu pun bisa berguna bagimu. Para Leviathan menampungku saat tidak ada yang mau. Kau menolong mereka, jadi aku menolongmu sebagai balasan. Berhati-hatilah: memakai benda-benda ini ada harganya.",
				},
				1
			)

			player.quest[_leviathan] = 3
			_showMenu(player)
		end
	end),

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
	end,
}
