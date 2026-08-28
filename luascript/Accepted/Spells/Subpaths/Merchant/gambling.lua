gambling = {
	cast = async(function(player)
		local magicCost = 120
		local aethers = 30000

		if player.gmLevel ~= 0 then
			aethers = 0
		end

		if not player:canCast(1, 1, 0) then
			return
		end

		if player.magic < magicCost then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		player:setAether("gambling", aethers)

		local item = Item("coins_1")

		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		local input = player:inputLetterCheck(player:inputSeq(
			"Who shall be gambling?",
			"The player known as",
			"will be gambling.",
			{},
			{}
		))
		local target = Player(input)

		if target == nil then
			player:dialogSeq({t, "Pemain itu sedang tidak daring."}, 0)
			return
		end

		if not distanceSquare(player, target, 6) then
			player:dialogSeq({t, "Pemain itu tidak cukup dekat denganmu."}, 0)
			return
		end

		local goldAmounts = {1000, 10000, 100000}
		local goldChoices = {"1.000 Emas", "10.000 Emas", "100.000 Emas"}
		local goldChoice = player:menuSeq(
			"Mereka akan mempertaruhkan sejumlah",
			goldChoices,
			{}
		)

		target:freeAsync()
		gambling.presentOffer(target, player, goldAmounts[goldChoice])
	end),

	presentOffer = async(function(player, merchant, amount)
		local item = Item("coins_1")

		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		local confirm = player:menuSeq(
			"Seorang pedagang bersedia membantumu bertaruh sebesar " .. Tools.formatValue(amount) .. " Emas. Kau dan lawanmu masing-masing menyetor jumlah itu, dan pemenangnya menerima dua kali lipat. Kau setuju?",
			{"Ya", "Tidak"},
			{}
		)

		if confirm == 1 then
			if player.money < amount then
				player:dialogSeq({t, "Emasmu tidak cukup."}, 0)
				return
			end
			merchant:freeAsync()
			gambling.confirmOffer(merchant, player, amount)

			-- returns back to mercahnt to type in the name of next person
		end
	end),

	confirmOffer = async(function(player, target1, amount)
		local item = Item("coins_1")
		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		local input = player:inputLetterCheck(player:inputSeq(
			"Who will be trying their luck against this person?",
			"The one known as",
			"will be gambling.",
			{},
			{}
		))
		local target2 = Player(input)

		if target2 == nil then
			player:dialogSeq({t, "Pemain itu sedang tidak daring."}, 0)
			return
		end

		if not distanceSquare(player, target2, 6) then
			player:dialogSeq({t, "Pemain itu tidak cukup dekat denganmu."}, 0)
			return
		end

		if target1.ID == target2.ID then
			player:dialogSeq(
				{t, "Orang itu tidak bisa bertaruh melawan dirinya sendiri."},
				0
			)
			return
		end

		target2:freeAsync()
		gambling.presentOffer2(target2, target1, player, amount)
	end),

	presentOffer2 = async(function(player, otherPlayer, merchant, amount)
		local item = Item("coins_1")
		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		local confirm = player:menuSeq(
			"Seorang pedagang bersedia membantumu bertaruh sebesar " .. Tools.formatValue(amount) .. " Emas. Kau dan lawanmu masing-masing menyetor jumlah itu, dan pemenangnya menerima dua kali lipat. Kau setuju?",
			{"Ya", "Tidak"},
			{}
		)

		if confirm == 1 then
			if player.money < amount then
				player:dialogSeq({t, "Emasmu tidak cukup."}, 0)
				return
			end
			merchant:freeAsync()
			gambling.run(merchant, player, otherPlayer, amount)

			-- returns back to mercahnt to type in the name of next person
		end
	end),

	run = async(function(player, target1, target2, amount)
		local item = Item("coins_1")
		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		player:dialogSeq(
			{t, "Kedua pihak menyetujui taruhan ini. Lanjutkan sampai selesai."},
			1
		)

		if target1 == nil or target2 == nil then
			player:dialogSeq({t, "Salah satu pihak keluar dari permainan."}, 0)
			return
		end

		if target1.money < amount then
			player:dialogSeq(
				{
					t,
					target1.name .. " mencoba curang; uang yang ia tawarkan untuk bertaruh sudah tidak ada."
				},
				0
			)
			return
		end
		if target2.money < amount then
			player:dialogSeq(
				{
					t,
					target2.name .. " mencoba curang; uang yang ia tawarkan untuk bertaruh sudah tidak ada."
				},
				0
			)
			return
		end

		local roll1 = math.random(1, 100)
		local roll2 = math.random(1, 100)

		if roll1 > roll2 then
			target1:sendAnimation(2, 3)
			target1:sendMinitext("Kau menang.")
			target2:sendMinitext("Kau kalah.")
			target1:addGold(amount)
			target2:removeGold(amount)
		elseif roll2 > roll1 then
			target2:sendAnimation(2, 3)
			target1:sendMinitext("Kau kalah.")
			target2:sendMinitext("Kau menang.")
			target1:removeGold(amount)
			target2:addGold(amount)
		else
			target1:sendMinitext("Hasilnya seri, jadi tidak ada emas yang diambil.")
			target2:sendMinitext("Hasilnya seri, jadi tidak ada emas yang diambil.")
			player:dialogSeq(
				{t, "Hasilnya seri. Tidak ada emas yang diambil dari kedua pihak."},
				0
			)
		end
	end),

	requirements = function(player)
		local level = 99
		local items = {0}
		local itemAmounts = {1000}
		local description = "A simple game of chance."
		return level, items, itemAmounts, description
	end
}
