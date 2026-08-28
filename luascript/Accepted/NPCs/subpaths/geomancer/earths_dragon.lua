NpcSubpathGeomancerEarthsDragonNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local options = {
			"Beli",
			"Jual",
			"Perbaiki Barang",
			"Perbaiki Semua Barang",
			"Penjaga Tanah Merah",
			"Reincarnate",
			"Observe"
		}
		local buysellopts = {"shu_jing"}

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			options
		)

		if choice == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				buysellopts
			)
		elseif choice == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				buysellopts
			)
		elseif choice == "Perbaiki Barang" then
			player:repairExtend()
		elseif choice == "Perbaiki Semua Barang" then
			player:repairAll(npc)
		elseif choice == "Keeper of the Red Soil" then
			player:dialogSeq(
				{
					t,
					"Aku hanya mencari mereka yang menjaga keseimbangan dan mengikuti ibu agung Tap."
				},
				0
			)
			return
		elseif choice == "Reincarnate" then
			general_npc_funcs.reincarnate(player, npc)
		elseif choice == "Observe" then
			general_npc_funcs.observe(player, npc)
		end
	end),

	move = function(npc)
		npc.side = math.random(0, 3)
		npc:sendSide()
	end,

	buyItems = function()
		local buyItems = {"shu_jing"}

		return buyItems
	end,

	sellItems = function()
		return NpcSubpathGeomancerEarthsDragonNpc.buyItems()
	end
}
