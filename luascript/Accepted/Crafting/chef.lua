chef = {
	craft = function(player, npc, speech)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local treqItem = {}
		local item = {}

		-- food preparation

		if speech == "siapkan mi" then
			local tNoodle = {graphic = convertGraphic(225, "item"), color = 0}

			local isAccomplished = crafting.checkSkillLevel(
				player,
				"food preparation",
				"accomplished"
			)

			-- returns if accomplished or better

			if not isAccomplished then
				player:dialogSeq(
					{
						tNoodle,
						"Kau harus lebih banyak berlatih menyiapkan bahan makanan. Setelah kau tahu cara menyiapkannya, barulah kau bisa memasaknya."
					},
					0
				)
				return
			end

			local itemsReq = {"salt_block", "flour", "water_jug"}

			for i = 1, #itemsReq do
				local item = Item(itemsReq[i])
				local tItemReq = {graphic = item.icon, color = item.iconC}

				if player:hasItem(itemsReq[i], 1) ~= true then
					player:dialogSeq(
						{
							tItemReq,
							"Kau tidak punya " .. item.name .. " untuk persiapan, kembalilah kalau kau sudah punya."
						},
						0
					)
					return
				end
			end

			for i = 1, #itemsReq do
				player:removeItem(itemsReq[i], 1, 9)
			end

			if math.random(1, 10) == 1 then
				crafting.skillChanceIncrease(player, npc, "chef")
				player:addItem("noodles", 1)
				player:sendMinitext("Kau berhasil.")
			else
				player:sendMinitext("Percobaanmu gagal.")
			end

			return
		end
	end
}
