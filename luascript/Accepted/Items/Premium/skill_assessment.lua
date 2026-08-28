skill_assessment = {
	use = async(function(player)
		local item = player:getInventoryItem(player.invSlot)

		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		local allSkills = {
			"woodworking",
			"metalworking",
			"jewelry making",
			"fishing",
			"weaving",
			"smelting",
			"gemcutting",
			"woodcutting",
			"mining",
			"farming",
			"food preparation",
			"chef",
			"potion making",
			"scribing",
			"tailoring"
		}
		local allSkillNames = {
			"Carpentry",
			"Smithing",
			"Jewelcrafting",
			"Fishing",
			"Weaving",
			"Smelting",
			"Gemcutting",
			"Woodcutting",
			"Mining",
			"Farming",
			"Food preparation",
			"Chef",
			"Alchemy",
			"Inscription",
			"Tailoring"
		}

		local hasSkills = {}
		local hasSkillsNames = {}
		local skillLevels = {}

		for i = 1, #allSkills do
			if crafting.checkSkillLegend(player, allSkills[i]) then
				table.insert(hasSkills, allSkills[i])
				table.insert(hasSkillsNames, allSkillNames[i])
			end
		end

		for i = 1, #hasSkillsNames do
			local level = crafting.getSkillLevel(player, hasSkills[i])

			if level == nil then
				level = "novice"
			end

			hasSkillsNames[i] = hasSkillsNames[i] .. " (Current level: " .. level .. ")"
		end

		local choice = player:menuSeq(
			"Pilih keahlian yang ingin kau tanyakan.",
			hasSkillsNames,
			{}
		)

		if player:hasItem("skill_assessment", 1) ~= true then
			player:dialogSeq({t, "Nice try."}, 0)
			return
		end

		player:removeItem("skill_assessment", 1)

		local chosenSkill = hasSkills[choice]
		local skillPtsTable = crafting.skillPointsPerLevel(chosenSkill)
		local hasPts = player.registry[chosenSkill]

		local nextPts = 0
		if not crafting.checkSkillLevel(player, chosenSkill, "legendary") then
			for i = 1, #skillPtsTable do
				if hasPts >= skillPtsTable[i] then
					nextPts = skillPtsTable[i + 1]
				end
			end
		else
			player:dialogSeq(
				{
					t,
					"Statusmu sudah legendaris dan keahlianmu tidak bisa ditingkatkan lagi."
				},
				0
			)
			return
		end
		leftPoints = nextPts - hasPts
		player:sendMinitext("Kemajuan Keahlian Sekarang: ")
		player:sendMinitext(leftPoints .. " menuju pangkat berikutnya.")

		local percentage = (hasPts / nextPts) * 100
		percentage = string.format("%.2f", percentage)

		player:dialogSeq(
			{
				t,
				"Kemajuan Keahlian Sekarang:                        " .. leftPoints .. " menuju pangkat berikutnya."
			},
			0
		)
	end)
}
