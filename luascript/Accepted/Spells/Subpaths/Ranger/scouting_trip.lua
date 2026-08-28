scouting_trip = {
	cast = async(function(player)
		player.npcGraphic = 0
		player.npcColor = 0
		player.dialogType = 0
		player.lastClick = player.ID

		local aethers = 25000000

		-- 25000s
		local magicCost = 2000

		if player.magic < magicCost then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		if player.gmLevel > 0 then
			aethers = 0
			magicCost = 0
		end

		player.magic = player.magic - magicCost
		player:sendStatus()
		player:setAether("scouting_trip", aethers)

		local choice = player:menuString(
			"Apa yang ingin kau lakukan?",
			{"Mark a person", "Remove a mark"}
		)

		if choice == "Mark a person" then
			local input = player:inputLetterCheck(player:inputSeq(
				"Who will be granted the mark?",
				"The noble",
				"has been scouting with me",
				{},
				{}
			))
			local target = Player(input)

			if target == nil then
				player:dialogSeq({t, "Pemain itu sedang tidak daring."}, 0)
				return
			end
			if target.ID == player.ID then
				player:dialogSeq(
					{t, "Kau tidak bisa memberi pengakuan untuk dirimu sendiri."},
					0
				)
				return
			end
			if not distanceSquare(player, target, 10) then
				player:dialogSeq({t, "Orang itu tidak berada di dekatmu."}, 0)
				return
			end

			target.registry["scouted_with_rangers"] = target.registry[
				"scouted_with_rangers"
			] + 1
			target:removeLegendbyName("scouted_with_rangers")
			target:addLegend(
				"Mengintai bersama Ranger " .. target.registry[
					"scouted_with_rangers"
				] .. " times, marked by $player (" .. curT() .. ")",
				"scouted_with_rangers",
				12,
				1,
				player.ID
			)

			player:dialogSeq(
				{t, target.name .. " telah diakui atas pengintaiannya."},
				1
			)

			target:freeAsync()
			target:dialogSeq(
				{t, "Kau telah diakui atas pengintaian bersama para Ranger."},
				0
			)
		elseif choice == "Remove a mark" then
			local input = player:inputLetterCheck(player:inputSeq(
				"Who will have their mark removed?",
				"The noble",
				"needs their mark removed.",
				{},
				{}
			))
			local target = Player(input)

			if target == nil then
				player:dialogSeq({t, "Pemain itu sedang tidak daring."}, 0)
				return
			end
			if target.ID == player.ID then
				player:dialogSeq(
					{t, "Kau tidak bisa memberi pengakuan untuk dirimu sendiri."},
					0
				)
				return
			end
			if not distanceSquare(player, target, 10) then
				player:dialogSeq({t, "Orang itu tidak berada di dekatmu."}, 0)
				return
			end

			target.registry["scouted_with_rangers"] = 0
			target:removeLegendbyName("scouted_with_rangers")

			player:dialogSeq(
				{t, target.name .. " tandanya telah dihapus."},
				1
			)

			target:freeAsync()
			target:dialogSeq(
				{
					t,
					"Tandamu atas Mengintai bersama Ranger telah dihapus oleh " .. player.name .. "."
				},
				0
			)
		end
	end),
	requirements = function(player)
		local level = 99
		local items = {Item("surge").id, Item("ju_jak_key").id, 0}
		local itemAmounts = {1, 1, 50000}
		local description = "Empower your mind."
		return level, items, itemAmounts, description
	end
}
