local _waypointId = "hamgyong_nam_do"

NoghNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		local opts = {
			"Beli",
			"Jual",
			"Bicara dengan Nogh",
			"Menambang Substratum",
			--"Rings of Substratum"
		}

		if (not Waypoint.isEnabled(player, _waypointId)) then
			table.insert(opts, "Waypoint")
		end

		local buyopts = {"ogre_cider", "ogre_drought"}

		local sellopts = {
			"wool",
			"ogre_cider",
			"ogre_drought",
			"ginseng_piece",
			"ginseng",
			"mountain_ginseng"
		}

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				buyopts
			)
			return
		elseif menu == "Jual" then
			player:sellExtend("What are you willing to sell today?", sellopts)
			return
		elseif menu == "Bicara dengan Nogh" then
			player:dialogSeq({"Aku menjual banyak cider bermutu. Mau beli?"}, 0)
			return
		elseif menu == "Menambang Substratum" then
			player:dialogSeq(
				{
					"Kudengar para ogre Hamgyong Nam-Do menambang sangat dalam ke perut bumi. Mereka bahkan sudah menembus batu dan tanah sampai ke Substratum.",
					"Aku tidak tahu ada apa di bawah sana, tetapi menurut legenda daerah itu rumah bumi yang hidup itu sendiri. Para ogre itu mungkin sedang mencari masalah; kulihat banyak peti perbekalan masuk ke tambang.",
					"Ini bisa berarti mereka merencanakan sesuatu yang besar, atau mereka sedang kesulitan di kedalaman sana. Kalau kau menemukan apa pun dari Substratum, bawakan padaku supaya bisa kuperiksa."
				},
				0
			)
			return
		elseif menu == "Rings of Substratum" then
			-- @TODO: Implement
			return
		elseif menu == "Waypoint" then
			Waypoint.add(player, npc, _waypointId)
		end
	end),

	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)

		if (speech == "titik jalan" and not Waypoint.isEnabled(player, _waypointId)) then
			Waypoint.add(player, npc, _waypointId)
			return
		end
	end),

	buyItems = function(npc)
		local buyItems = {"ogre_cider", "ogre_drought"}
		return buyItems
	end,

	sellItems = function(npc)
		local sellItems = {
			"wool",
			"ogre_cider",
			"ogre_drought",
			"ginseng_piece",
			"ginseng",
			"mountain_ginseng"
		}
		return sellItems
	end
}
