honor_of_the_three_sects = {
	cast = async(function(player)
		local t = {graphic = convertGraphic(803, "item"), color = 0}

		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = player.ID

		local aethers = 5000
		local magicCost = 100

		if player.magic < magicCost then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		if player.gmLevel > 0 then
			aethers = 0
		end

		player.magic = player.magic - magicCost
		player:sendStatus()
		player:setAether("honor_of_the_three_sects", aethers)

		local input = player:inputLetterCheck(player:inputSeq(
			"Whom is it that has caught your eye?",
			"The one known as",
			"has caught my eye",
			{},
			{}
		))
		local target = Player(input)

		if target == nil then
			player:dialogSeq({t, "Pemain itu sedang tidak daring."}, 0)
			return
		end
		if target.ID == player.ID then
			player:dialogSeq({t, "Kau tidak bisa memberi pengakuan untuk dirimu sendiri."}, 0)
			return
		end

		local opts = {
			"Elemental Arts recognition",
			"Pengakuan Aliran Qi",
			"Sage Study recognition",
			"Remove recognition"
		}
		local legendMarkyname = {
			"practiced_the_elemental_arts",
			"balanced_the_flow_of_qi",
			"studied_with_the_sages"
		}
		local legendMarks = {
			"Practiced the elemental arts",
			"Balanced the flow of Qi",
			"Studied with the Sages"
		}
		local choice = player:menuString(
			"Apa yang ingin kau anugerahkan kepadanya?",
			opts
		)

		if choice == "Elemental Arts recognition" then
			target.registry["practiced_the_elemental_arts"] = target.registry[
				"practiced_the_elemental_arts"
			] + 1
			target:removeLegendbyName("practiced_the_elemental_arts")
			target:addLegend(
				"Mempraktikkan Seni Unsur " .. player.registry[
					"practiced_the_elemental_arts"
				] .. " times, recognized by $player",
				"practiced_the_elemental_arts",
				76,
				5,
				player.ID
			)
			player:dialogSeq(
				{
					t,
					target.name .. " telah diakui atas \"Mempraktikkan Seni Unsur\""
				},
				0
			)
			return
		elseif choice == "Pengakuan Aliran Qi" then
			target.registry["balanced_the_flow_of_qi"] = target.registry[
				"balanced_the_flow_of_qi"
			] + 1
			target:removeLegendbyName("balanced_the_flow_of_qi")
			target:addLegend(
				"Menyeimbangkan aliran Qi " .. target.registry[
					"balanced_the_flow_of_qi"
				] .. " times, recognized by $player",
				"balanced_the_flow_of_qi",
				122,
				61,
				player.ID
			)
			player:dialogSeq(
				{
					t,
					target.name .. " telah diakui atas \"Menyeimbangkan aliran Qi\""
				},
				0
			)
			return
		elseif choice == "Sage Study recognition" then
			target.registry["studied_with_the_sages"] = target.registry[
				"studied_with_the_sages"
			] + 1
			target:removeLegendbyName("studied_with_the_sages")
			target:addLegend(
				"Berguru pada para Bijak " .. target.registry[
					"studied_with_the_sages"
				] .. " times, recognized by $player",
				"studied_with_the_sages",
				145,
				61,
				player.ID
			)
			player:dialogSeq(
				{
					t,
					target.name .. " telah diakui atas \"Berguru pada para Bijak\""
				},
				0
			)
			return
		elseif choice == "Remove recognition" then
			local foundLegendMarks = {}
			for i = 1, #legendMarkyname do
				if target:hasLegend(legendMarkyname[i]) then
					table.insert(foundLegendMarks, legendMarks[i])
				end
			end

			local lmChoice = player:menuString(
				"Pengakuan mana yang ingin kau hapus?",
				foundLegendMarks
			)

			for i = 1, #legendMarks do
				if lmChoice == legendMarks[i] then
					player:removeLegendbyName(legendMarkyname[i])
					break
				end
			end

			player:dialogSeq(
				{
					t,
					target.name .. " kehilangan pengakuannya karena " .. lmChoice
				},
				0
			)
			return
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
